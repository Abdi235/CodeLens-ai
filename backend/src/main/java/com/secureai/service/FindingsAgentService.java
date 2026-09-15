package com.secureai.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.secureai.agent.GeminiOpsAgentBrain;
import com.secureai.agent.HeuristicFindingsAgentBrain;
import com.secureai.agent.FindingsToolRegistry;
import com.secureai.agent.OpenAiOpsAgentBrain;
import com.secureai.agent.OpsAgentBrain;
import com.secureai.agent.OpsTool;
import com.secureai.agent.OpsToolContext;
import com.secureai.dto.FindingsAgentEvalResponse;
import com.secureai.dto.FindingsAgentRunRequest;
import com.secureai.dto.FindingsAgentRunResponse;
import com.secureai.dto.FindingsAgentSimulateRequest;
import com.secureai.model.FindingsAgentRun;
import com.secureai.model.FindingsAgentRunStatus;
import com.secureai.model.FindingsAgentStep;
import com.secureai.model.Project;
import com.secureai.model.Scan;
import com.secureai.model.ScanStatus;
import com.secureai.model.Severity;
import com.secureai.model.TriageStatus;
import com.secureai.model.User;
import com.secureai.model.Vulnerability;
import com.secureai.repository.FindingsAgentRunRepository;
import com.secureai.repository.ProjectRepository;
import com.secureai.repository.ScanRepository;
import com.secureai.repository.VulnerabilityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class FindingsAgentService {

    private static final String SYSTEM_PROMPT = """
            You are CodeLens Findings Triage Agent.
            You review vulnerability findings, gather code context, propose fixes, and mark triage outcomes.
            Do not invent tools. Preferred flow:
            1) list_findings
            2) get_finding for the highest-severity open item
            3) search_code when context would help
            4) propose_fix
            5) mark_triaged (TRIAGED / FALSE_POSITIVE / RESOLVED)
            6) always finish with finish_triage
            Be concise. Never claim you ran a tool without calling it.
            """;

    private final FindingsToolRegistry toolRegistry;
    private final GeminiOpsAgentBrain geminiOpsAgentBrain;
    private final OpenAiOpsAgentBrain openAiOpsAgentBrain;
    private final HeuristicFindingsAgentBrain heuristicFindingsAgentBrain;
    private final FindingsAgentRunRepository runRepository;
    private final CurrentUserService currentUserService;
    private final ProjectRepository projectRepository;
    private final ScanRepository scanRepository;
    private final VulnerabilityRepository vulnerabilityRepository;
    private final JsonMapper objectMapper;

    @Value("${secureai.findings-agent.max-steps:8}")
    private int maxSteps;

    @Value("${secureai.ops-agent.prefer-gemini:true}")
    private boolean preferGemini;

    @Value("${secureai.ops-agent.prefer-openai:false}")
    private boolean preferOpenai;

    @Transactional
    public FindingsAgentRunResponse run(FindingsAgentRunRequest request) {
        User user = currentUserService.requireCurrentUser();
        return execute(user, "live", request.goal(), request.dryRun(), null, null);
    }

    @Transactional
    public FindingsAgentRunResponse simulate(FindingsAgentSimulateRequest request) {
        User user = currentUserService.requireCurrentUser();
        String scenario = request.scenario().trim().toLowerCase(Locale.ROOT);
        List<String> expected = setupScenario(scenario, user);
        String goal = switch (scenario) {
            case "critical_open_finding" ->
                    "Triage the highest-severity open finding: inspect it, propose a fix, mark it triaged, then finish.";
            case "false_positive" ->
                    "Review the open finding. If it looks like a false positive, mark FALSE_POSITIVE and finish triage.";
            case "clean_queue" ->
                    "Check open high/critical findings. If none, finish triage as a healthy queue.";
            default -> throw new IllegalArgumentException(
                    "Unknown scenario. Use critical_open_finding | false_positive | clean_queue");
        };
        return execute(user, "simulate", goal, request.dryRun(), scenario, expected);
    }

    @Transactional(readOnly = true)
    public FindingsAgentRunResponse getRun(String runId) {
        FindingsAgentRun run = runRepository.findByRunId(runId)
                .orElseThrow(() -> new IllegalArgumentException("Findings agent run not found"));
        return toResponse(run);
    }

    @Transactional(readOnly = true)
    public List<FindingsAgentRunResponse> listRuns() {
        return runRepository.findTop20ByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public FindingsAgentEvalResponse eval() {
        List<FindingsAgentRun> runs = runRepository.findAll().stream()
                .filter(r -> r.getSuccess() != null)
                .toList();
        long evaluated = runs.size();
        long successful = runs.stream().filter(r -> Boolean.TRUE.equals(r.getSuccess())).count();
        double avgDuration = runs.stream()
                .map(FindingsAgentRun::getDurationMs)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);
        double avgSteps = runs.stream()
                .mapToInt(r -> r.getSteps() == null ? 0 : r.getSteps().size())
                .average()
                .orElse(0);
        double rate = evaluated == 0 ? 0.0 : Math.round((successful * 1000.0) / evaluated) / 10.0;
        return new FindingsAgentEvalResponse(
                evaluated,
                successful,
                rate,
                Math.round(avgDuration * 10.0) / 10.0,
                Math.round(avgSteps * 10.0) / 10.0
        );
    }

    private FindingsAgentRunResponse execute(
            User user,
            String mode,
            String goal,
            boolean dryRun,
            String scenario,
            List<String> expectedTools
    ) {
        long started = System.currentTimeMillis();
        toolRegistry.resetRunState();

        OpsAgentBrain brain = selectBrain();
        FindingsAgentRun run = FindingsAgentRun.builder()
                .user(user)
                .mode(mode)
                .goal(goal)
                .status(FindingsAgentRunStatus.RUNNING)
                .brainType(brain.type())
                .dryRun(dryRun)
                .scenario(scenario)
                .expectedToolsJson(expectedTools == null ? null : writeJson(expectedTools))
                .build();
        run = runRepository.save(run);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        messages.add(Map.of("role", "user", "content", goal));

        int stepIndex = 0;
        try {
            List<OpsTool> tools = toolRegistry.tools();
            for (int turn = 0; turn < maxSteps; turn++) {
                OpsAgentBrain.Decision decision = brain.decide(messages, tools);

                if (decision.assistantContent() != null && !decision.assistantContent().isBlank()
                        && !decision.hasToolCalls()) {
                    run.addStep(FindingsAgentStep.builder()
                            .stepIndex(stepIndex++)
                            .role("assistant")
                            .content(decision.assistantContent())
                            .build());
                }

                if (!decision.hasToolCalls()) {
                    if (!toolRegistry.isFinished()) {
                        Map<String, Object> forced = toolRegistry.require("finish_triage")
                                .execute(objectMapper.readTree(
                                                "{\"summary\":\"Stopped without explicit finish_triage; marking complete.\",\"healthyQueue\":false}"),
                                        new OpsToolContext(dryRun, run.getRunId()));
                        run.addStep(FindingsAgentStep.builder()
                                .stepIndex(stepIndex++)
                                .role("tool")
                                .toolName("finish_triage")
                                .toolArgsJson("{\"summary\":\"auto-finalize\"}")
                                .toolResultJson(writeJson(forced))
                                .build());
                    }
                    break;
                }

                List<Map<String, Object>> toolCallPayloads = new ArrayList<>();
                for (OpsAgentBrain.ToolCall tc : decision.toolCalls()) {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("id", tc.id());
                    payload.put("type", "function");
                    payload.put("function", Map.of(
                            "name", tc.name(),
                            "arguments", tc.argumentsJson() == null ? "{}" : tc.argumentsJson()
                    ));
                    if (tc.thoughtSignature() != null && !tc.thoughtSignature().isBlank()) {
                        payload.put("thoughtSignature", tc.thoughtSignature());
                    }
                    toolCallPayloads.add(payload);
                    run.addStep(FindingsAgentStep.builder()
                            .stepIndex(stepIndex++)
                            .role("assistant")
                            .toolName(tc.name())
                            .toolArgsJson(tc.argumentsJson())
                            .content(decision.assistantContent())
                            .build());
                }
                Map<String, Object> assistantMsg = new LinkedHashMap<>();
                assistantMsg.put("role", "assistant");
                assistantMsg.put("content", decision.assistantContent() == null ? "" : decision.assistantContent());
                assistantMsg.put("tool_calls", toolCallPayloads);
                messages.add(assistantMsg);

                for (OpsAgentBrain.ToolCall tc : decision.toolCalls()) {
                    OpsTool tool = toolRegistry.require(tc.name());
                    JsonNode args = objectMapper.readTree(
                            tc.argumentsJson() == null || tc.argumentsJson().isBlank() ? "{}" : tc.argumentsJson());
                    Map<String, Object> result = tool.execute(args, new OpsToolContext(dryRun, run.getRunId()));
                    String resultJson = writeJson(result);
                    run.addStep(FindingsAgentStep.builder()
                            .stepIndex(stepIndex++)
                            .role("tool")
                            .toolName(tc.name())
                            .toolArgsJson(tc.argumentsJson())
                            .toolResultJson(resultJson)
                            .build());
                    Map<String, Object> toolMsg = new LinkedHashMap<>();
                    toolMsg.put("role", "tool");
                    toolMsg.put("tool_call_id", tc.id());
                    toolMsg.put("name", tc.name());
                    toolMsg.put("content", resultJson);
                    messages.add(toolMsg);
                }

                if (toolRegistry.isFinished()) {
                    break;
                }
            }

            run.setStatus(FindingsAgentRunStatus.COMPLETED);
            run.setSummary(toolRegistry.getFinishSummary());
            run.setSuccess(evaluateSuccess(expectedTools, run));
        } catch (Exception e) {
            log.error("Findings agent run failed runId={}", run.getRunId(), e);
            run.setStatus(FindingsAgentRunStatus.FAILED);
            run.setSummary(e.getMessage());
            run.setSuccess(false);
            run.addStep(FindingsAgentStep.builder()
                    .stepIndex(stepIndex)
                    .role("system")
                    .content("ERROR: " + e.getMessage())
                    .build());
        }

        run.setCompletedAt(Instant.now());
        run.setDurationMs(System.currentTimeMillis() - started);
        run = runRepository.save(run);
        return toResponse(run);
    }

    private OpsAgentBrain selectBrain() {
        if (preferGemini && geminiOpsAgentBrain.isConfigured()) {
            return geminiOpsAgentBrain;
        }
        if (preferOpenai && openAiOpsAgentBrain.isConfigured()) {
            return openAiOpsAgentBrain;
        }
        return heuristicFindingsAgentBrain;
    }

    private List<String> setupScenario(String scenario, User user) {
        return switch (scenario) {
            case "critical_open_finding" -> {
                seedFinding(user, Severity.CRITICAL, TriageStatus.OPEN, "SQL Injection");
                yield List.of("list_findings", "get_finding", "propose_fix", "mark_triaged", "finish_triage");
            }
            case "false_positive" -> {
                seedFinding(user, Severity.MEDIUM, TriageStatus.OPEN, "Hardcoded Credential");
                yield List.of("list_findings", "get_finding", "mark_triaged", "finish_triage");
            }
            case "clean_queue" -> {
                // Ensure no OPEN high/critical for this user by seeding only a resolved item.
                seedFinding(user, Severity.HIGH, TriageStatus.RESOLVED, "XSS");
                yield List.of("list_findings", "finish_triage");
            }
            default -> throw new IllegalArgumentException("Unknown scenario: " + scenario);
        };
    }

    private void seedFinding(User user, Severity severity, TriageStatus status, String type) {
        Project project = projectRepository.save(Project.builder()
                .user(user)
                .name("findings-agent-sim-" + Instant.now().toEpochMilli())
                .repositoryUrl("https://example.com/codelens-sim.git")
                .build());
        Scan scan = scanRepository.save(Scan.builder()
                .project(project)
                .status(ScanStatus.COMPLETED)
                .startedAt(Instant.now().minusSeconds(60))
                .completedAt(Instant.now())
                .vulnerabilityCount(1)
                .build());
        vulnerabilityRepository.save(Vulnerability.builder()
                .scan(scan)
                .severity(severity)
                .type(type)
                .fileLocation("src/main/java/DemoController.java")
                .lineNumber(42)
                .description("Simulated " + type + " finding for findings-agent scenario.")
                .recommendation("Apply secure coding remediation for " + type)
                .triageStatus(status)
                .build());
    }

    private Boolean evaluateSuccess(List<String> expectedTools, FindingsAgentRun run) {
        if (expectedTools == null || expectedTools.isEmpty()) {
            return toolRegistry.isFinished();
        }
        Set<String> used = new HashSet<>();
        for (FindingsAgentStep step : run.getSteps()) {
            if (step.getToolName() != null) {
                used.add(step.getToolName());
            }
        }
        for (String expected : expectedTools) {
            if (!used.contains(expected)) {
                return false;
            }
        }
        return true;
    }

    private FindingsAgentRunResponse toResponse(FindingsAgentRun run) {
        List<FindingsAgentRunResponse.Step> steps = new ArrayList<>();
        for (FindingsAgentStep s : run.getSteps()) {
            steps.add(new FindingsAgentRunResponse.Step(
                    s.getStepIndex(),
                    s.getRole(),
                    s.getToolName(),
                    readMap(s.getToolArgsJson()),
                    readMap(s.getToolResultJson()),
                    s.getContent()
            ));
        }
        return new FindingsAgentRunResponse(
                run.getRunId(),
                run.getMode(),
                run.getGoal(),
                run.getStatus().name(),
                run.getBrainType(),
                run.isDryRun(),
                run.getScenario(),
                run.getSuccess(),
                run.getSummary(),
                run.getDurationMs(),
                steps
        );
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of("raw", json);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{\"_error\":\"serialize_failed\"}";
        }
    }
}
