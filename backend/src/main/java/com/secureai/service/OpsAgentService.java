package com.secureai.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.secureai.agent.*;
import com.secureai.dto.*;
import com.secureai.model.*;
import com.secureai.monitoring.HttpRequestMetrics;
import com.secureai.repository.OpsAgentRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpsAgentService {

    private static final String SYSTEM_PROMPT = """
            You are CodeLens Ops Agent — an autonomous operations agent.
            You observe live service health and MUST choose tools to remediate issues.
            Do not invent tools. Prefer: observe with get_system_health and list_stuck_jobs,
            then remediations (requeue_job, wake_worker, wake_ai, wake_api, page_human),
            and always finish by calling resolve_incident.
            Be concise. Never claim you ran a tool without actually calling it.
            """;

    private final OpsToolRegistry toolRegistry;
    private final OpenAiOpsAgentBrain openAiOpsAgentBrain;
    private final HeuristicOpsAgentBrain heuristicOpsAgentBrain;
    private final OpsAgentRunRepository runRepository;
    private final AnalysisJobService analysisJobService;
    private final CurrentUserService currentUserService;
    private final HttpRequestMetrics httpRequestMetrics;
    private final JsonMapper objectMapper;

    @Value("${secureai.ops-agent.max-steps:8}")
    private int maxSteps;

    @Value("${secureai.ops-agent.prefer-openai:true}")
    private boolean preferOpenai;

    @Transactional
    public OpsAgentRunResponse run(OpsAgentRunRequest request) {
        User user = currentUserService.requireCurrentUser();
        return execute(user, "live", request.goal(), request.dryRun(), null, null);
    }

    @Transactional
    public OpsAgentRunResponse simulate(OpsAgentSimulateRequest request) {
        User user = currentUserService.requireCurrentUser();
        String scenario = request.scenario().trim().toLowerCase(Locale.ROOT);
        List<String> expected = setupScenario(scenario, user);
        String goal = switch (scenario) {
            case "stuck_queued_job" ->
                    "A analysis job appears stuck in QUEUED. Diagnose and remediate, then resolve.";
            case "worker_down" ->
                    "The analysis worker may be asleep/down. Diagnose health and wake it if needed, then resolve.";
            case "elevated_error_rate" ->
                    "API error rate looks elevated. Investigate and page a human if warranted, then resolve.";
            case "healthy" ->
                    "Check whether the platform is healthy. If nothing is wrong, resolve as healthy.";
            default -> throw new IllegalArgumentException(
                    "Unknown scenario. Use stuck_queued_job | worker_down | elevated_error_rate | healthy");
        };
        return execute(user, "simulate", goal, request.dryRun(), scenario, expected);
    }

    @Transactional(readOnly = true)
    public OpsAgentRunResponse getRun(String runId) {
        OpsAgentRun run = runRepository.findByRunId(runId)
                .orElseThrow(() -> new IllegalArgumentException("Ops agent run not found"));
        return toResponse(run);
    }

    @Transactional(readOnly = true)
    public List<OpsAgentRunResponse> listRuns() {
        return runRepository.findTop20ByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public OpsAgentEvalResponse eval() {
        List<OpsAgentRun> runs = runRepository.findAll().stream()
                .filter(r -> r.getSuccess() != null)
                .toList();
        long evaluated = runs.size();
        long successful = runs.stream().filter(r -> Boolean.TRUE.equals(r.getSuccess())).count();
        double avgDuration = runs.stream()
                .map(OpsAgentRun::getDurationMs)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);
        double avgSteps = runs.stream()
                .mapToInt(r -> r.getSteps() == null ? 0 : r.getSteps().size())
                .average()
                .orElse(0);
        double rate = evaluated == 0 ? 0.0 : Math.round((successful * 1000.0) / evaluated) / 10.0;
        return new OpsAgentEvalResponse(
                evaluated,
                successful,
                rate,
                Math.round(avgDuration * 10.0) / 10.0,
                Math.round(avgSteps * 10.0) / 10.0
        );
    }

    private OpsAgentRunResponse execute(
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
        OpsAgentRun run = OpsAgentRun.builder()
                .user(user)
                .mode(mode)
                .goal(goal)
                .status(OpsAgentRunStatus.RUNNING)
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
                    run.addStep(OpsAgentStep.builder()
                            .stepIndex(stepIndex++)
                            .role("assistant")
                            .content(decision.assistantContent())
                            .build());
                }

                if (!decision.hasToolCalls()) {
                    if (!toolRegistry.isResolved()) {
                        // Force a resolve so the loop always terminates with an explicit outcome
                        Map<String, Object> forced = toolRegistry.require("resolve_incident")
                                .execute(objectMapper.readTree(
                                                "{\"summary\":\"Stopped without explicit resolve_incident; marking complete.\",\"healthy\":false}"),
                                        new OpsToolContext(dryRun, run.getRunId()));
                        run.addStep(OpsAgentStep.builder()
                                .stepIndex(stepIndex++)
                                .role("tool")
                                .toolName("resolve_incident")
                                .toolArgsJson("{\"summary\":\"auto-finalize\"}")
                                .toolResultJson(writeJson(forced))
                                .build());
                    }
                    break;
                }

                // Record assistant tool call message (OpenAI transcript shape)
                List<Map<String, Object>> toolCallPayloads = new ArrayList<>();
                for (OpsAgentBrain.ToolCall tc : decision.toolCalls()) {
                    toolCallPayloads.add(Map.of(
                            "id", tc.id(),
                            "type", "function",
                            "function", Map.of("name", tc.name(), "arguments", tc.argumentsJson())
                    ));
                    run.addStep(OpsAgentStep.builder()
                            .stepIndex(stepIndex++)
                            .role("assistant")
                            .toolName(tc.name())
                            .toolArgsJson(tc.argumentsJson())
                            .content(decision.assistantContent())
                            .build());
                }
                Map<String, Object> assistantMsg = new LinkedHashMap<>();
                assistantMsg.put("role", "assistant");
                if (decision.assistantContent() != null) {
                    assistantMsg.put("content", decision.assistantContent());
                } else {
                    assistantMsg.put("content", "");
                }
                assistantMsg.put("tool_calls", toolCallPayloads);
                messages.add(assistantMsg);

                for (OpsAgentBrain.ToolCall tc : decision.toolCalls()) {
                    OpsTool tool = toolRegistry.require(tc.name());
                    JsonNode args = objectMapper.readTree(
                            tc.argumentsJson() == null || tc.argumentsJson().isBlank() ? "{}" : tc.argumentsJson());
                    Map<String, Object> result = tool.execute(args, new OpsToolContext(dryRun, run.getRunId()));
                    String resultJson = writeJson(result);
                    run.addStep(OpsAgentStep.builder()
                            .stepIndex(stepIndex++)
                            .role("tool")
                            .toolName(tc.name())
                            .toolArgsJson(tc.argumentsJson())
                            .toolResultJson(resultJson)
                            .build());
                    messages.add(Map.of(
                            "role", "tool",
                            "tool_call_id", tc.id(),
                            "content", resultJson
                    ));
                }

                if (toolRegistry.isResolved()) {
                    break;
                }
            }

            run.setStatus(OpsAgentRunStatus.COMPLETED);
            run.setSummary(toolRegistry.getResolveSummary());
            run.setSuccess(evaluateSuccess(expectedTools, run));
        } catch (Exception e) {
            log.error("Ops agent run failed runId={}", run.getRunId(), e);
            run.setStatus(OpsAgentRunStatus.FAILED);
            run.setSummary(e.getMessage());
            run.setSuccess(false);
            run.addStep(OpsAgentStep.builder()
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
        if (preferOpenai && openAiOpsAgentBrain.isConfigured()) {
            return openAiOpsAgentBrain;
        }
        return heuristicOpsAgentBrain;
    }

    private List<String> setupScenario(String scenario, User user) {
        return switch (scenario) {
            case "stuck_queued_job" -> {
                analysisJobService.createStuckJobForSimulation(user, Instant.now().minus(30, ChronoUnit.MINUTES));
                yield List.of("list_stuck_jobs", "requeue_job", "resolve_incident");
            }
            case "worker_down" -> List.of("get_system_health", "wake_worker", "resolve_incident");
            case "elevated_error_rate" -> {
                httpRequestMetrics.injectServerErrors(25);
                yield List.of("get_system_health", "page_human", "resolve_incident");
            }
            case "healthy" -> List.of("get_system_health", "resolve_incident");
            default -> throw new IllegalArgumentException("Unknown scenario: " + scenario);
        };
    }

    private Boolean evaluateSuccess(List<String> expectedTools, OpsAgentRun run) {
        if (expectedTools == null || expectedTools.isEmpty()) {
            return toolRegistry.isResolved() ? Boolean.TRUE : Boolean.FALSE;
        }
        Set<String> used = new HashSet<>();
        for (OpsAgentStep step : run.getSteps()) {
            if (step.getToolName() != null && "tool".equals(step.getRole())) {
                used.add(step.getToolName());
            }
            // Also count assistant-declared tools that were executed (tool role preferred)
            if (step.getToolName() != null && "assistant".equals(step.getRole())) {
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

    private OpsAgentRunResponse toResponse(OpsAgentRun run) {
        List<OpsAgentRunResponse.Step> steps = new ArrayList<>();
        for (OpsAgentStep s : run.getSteps()) {
            steps.add(new OpsAgentRunResponse.Step(
                    s.getStepIndex(),
                    s.getRole(),
                    s.getToolName(),
                    readMap(s.getToolArgsJson()),
                    readMap(s.getToolResultJson()),
                    s.getContent()
            ));
        }
        return new OpsAgentRunResponse(
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
