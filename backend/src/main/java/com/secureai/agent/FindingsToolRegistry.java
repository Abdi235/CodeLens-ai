package com.secureai.agent;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.secureai.dto.CodeSearchResponse;
import com.secureai.dto.FixGenerateRequest;
import com.secureai.dto.FixGenerateResponse;
import com.secureai.dto.VulnerabilityResponse;
import com.secureai.model.AnalysisJob;
import com.secureai.model.TriageStatus;
import com.secureai.repository.AnalysisJobRepository;
import com.secureai.service.CodeSearchService;
import com.secureai.service.CurrentUserService;
import com.secureai.service.ScanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tools for the Findings Triage Agent: list/rank findings, pull code context,
 * propose fixes, and mark triage outcomes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FindingsToolRegistry {

    private final ScanService scanService;
    private final CodeSearchService codeSearchService;
    private final AnalysisJobRepository analysisJobRepository;
    private final CurrentUserService currentUserService;
    private final JsonMapper objectMapper;

    private volatile boolean finished;
    private volatile String finishSummary = "";

    public void resetRunState() {
        finished = false;
        finishSummary = "";
    }

    public boolean isFinished() {
        return finished;
    }

    public String getFinishSummary() {
        return finishSummary;
    }

    public OpsTool require(String name) {
        return tools().stream()
                .filter(t -> t.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown findings tool: " + name));
    }

    public List<OpsTool> tools() {
        List<OpsTool> tools = new ArrayList<>();

        tools.add(tool(
                "list_findings",
                "List the current user's scan vulnerabilities. Optionally filter by minSeverity (CRITICAL|HIGH|MEDIUM|LOW|INFO) or triageStatus (OPEN|TRIAGED|FALSE_POSITIVE|RESOLVED|ALL).",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "minSeverity", Map.of("type", "string", "description", "Minimum severity to include"),
                                "triageStatus", Map.of("type", "string", "description", "Filter by triage status; default OPEN")
                        ),
                        "additionalProperties", false
                ),
                (args, ctx) -> {
                    String minSev = text(args, "minSeverity");
                    String statusFilter = text(args, "triageStatus");
                    if (statusFilter.isBlank()) {
                        statusFilter = "OPEN";
                    }
                    List<VulnerabilityResponse> all = scanService.listAllMine();
                    List<Map<String, Object>> items = new ArrayList<>();
                    for (VulnerabilityResponse v : all) {
                        String status = v.triageStatus() == null ? "OPEN" : v.triageStatus().name();
                        if (!statusFilter.equalsIgnoreCase("ALL") && !status.equalsIgnoreCase(statusFilter)) {
                            continue;
                        }
                        if (!minSev.isBlank() && severityRank(v.severity().name()) < severityRank(minSev)) {
                            continue;
                        }
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("id", v.id());
                        row.put("severity", v.severity().name());
                        row.put("type", v.type());
                        row.put("fileLocation", v.fileLocation());
                        row.put("lineNumber", v.lineNumber());
                        row.put("triageStatus", status);
                        row.put("description", truncate(v.description(), 240));
                        items.add(row);
                    }
                    items.sort((a, b) -> Integer.compare(
                            severityRank(String.valueOf(b.get("severity"))),
                            severityRank(String.valueOf(a.get("severity")))));
                    return Map.of("count", items.size(), "findings", items);
                }
        ));

        tools.add(tool(
                "get_finding",
                "Get full details for one vulnerability by id.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "vulnerabilityId", Map.of("type", "integer", "description", "Vulnerability id")
                        ),
                        "required", List.of("vulnerabilityId"),
                        "additionalProperties", false
                ),
                (args, ctx) -> {
                    long id = args.path("vulnerabilityId").asLong(0);
                    if (id <= 0) {
                        return Map.of("ok", false, "error", "vulnerabilityId is required");
                    }
                    VulnerabilityResponse v = scanService.getVulnerability(id);
                    Map<String, Object> out = new LinkedHashMap<>(objectMapper.convertValue(v, Map.class));
                    out.put("ok", true);
                    return out;
                }
        ));

        tools.add(tool(
                "search_code",
                "BM25 search over an indexed analysis job for remediation context. Pass jobId when known; otherwise uses the user's latest analysis job.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of("type", "string", "description", "Search query (finding type, symbol, or path)"),
                                "jobId", Map.of("type", "string", "description", "Optional analysis job id"),
                                "limit", Map.of("type", "integer", "description", "Max hits (default 5)")
                        ),
                        "required", List.of("query"),
                        "additionalProperties", false
                ),
                (args, ctx) -> {
                    String query = text(args, "query");
                    if (query.isBlank()) {
                        return Map.of("ok", false, "error", "query is required");
                    }
                    String jobId = text(args, "jobId");
                    if (jobId.isBlank()) {
                        Long userId = currentUserService.requireCurrentUser().getId();
                        List<AnalysisJob> jobs = analysisJobRepository.findByUserIdOrderByCreatedAtDesc(userId);
                        if (jobs.isEmpty()) {
                            return Map.of(
                                    "ok", false,
                                    "error", "No analysis jobs indexed for BM25 search. Continue with get_finding + propose_fix."
                            );
                        }
                        jobId = jobs.getFirst().getJobId();
                    }
                    int limit = args.path("limit").asInt(5);
                    try {
                        CodeSearchResponse resp = codeSearchService.search(jobId, query, Math.max(1, Math.min(limit, 10)));
                        Map<String, Object> out = new LinkedHashMap<>(objectMapper.convertValue(resp, Map.class));
                        out.put("ok", true);
                        return out;
                    } catch (Exception e) {
                        return Map.of("ok", false, "error", e.getMessage(), "jobId", jobId);
                    }
                }
        ));

        tools.add(tool(
                "propose_fix",
                "Generate a remediation fix for a vulnerability (persists suggestedFix / aiExplanation).",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "vulnerabilityId", Map.of("type", "integer"),
                                "codeSnippet", Map.of("type", "string", "description", "Optional code context override")
                        ),
                        "required", List.of("vulnerabilityId"),
                        "additionalProperties", false
                ),
                (args, ctx) -> {
                    long id = args.path("vulnerabilityId").asLong(0);
                    if (id <= 0) {
                        return Map.of("ok", false, "error", "vulnerabilityId is required");
                    }
                    if (ctx.dryRun()) {
                        return Map.of("ok", true, "dryRun", true, "action", "propose_fix", "vulnerabilityId", id);
                    }
                    String snippet = text(args, "codeSnippet");
                    FixGenerateResponse fix = scanService.generateFix(new FixGenerateRequest(
                            id,
                            snippet.isBlank() ? null : snippet
                    ));
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("ok", true);
                    out.put("vulnerabilityId", fix.vulnerabilityId());
                    out.put("before", fix.before());
                    out.put("after", fix.after());
                    out.put("explanation", fix.explanation());
                    return out;
                }
        ));

        tools.add(tool(
                "mark_triaged",
                "Update triage status for a finding: TRIAGED, FALSE_POSITIVE, or RESOLVED.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "vulnerabilityId", Map.of("type", "integer"),
                                "status", Map.of("type", "string", "description", "TRIAGED | FALSE_POSITIVE | RESOLVED"),
                                "note", Map.of("type", "string")
                        ),
                        "required", List.of("vulnerabilityId", "status"),
                        "additionalProperties", false
                ),
                (args, ctx) -> {
                    long id = args.path("vulnerabilityId").asLong(0);
                    String statusRaw = text(args, "status").toUpperCase(Locale.ROOT);
                    String note = text(args, "note");
                    if (id <= 0 || statusRaw.isBlank()) {
                        return Map.of("ok", false, "error", "vulnerabilityId and status are required");
                    }
                    TriageStatus status;
                    try {
                        status = TriageStatus.valueOf(statusRaw);
                    } catch (Exception e) {
                        return Map.of("ok", false, "error", "Invalid status. Use TRIAGED|FALSE_POSITIVE|RESOLVED");
                    }
                    if (status == TriageStatus.OPEN) {
                        return Map.of("ok", false, "error", "Use TRIAGED, FALSE_POSITIVE, or RESOLVED");
                    }
                    if (ctx.dryRun()) {
                        return Map.of("ok", true, "dryRun", true, "action", "mark_triaged",
                                "vulnerabilityId", id, "status", status.name());
                    }
                    VulnerabilityResponse updated = scanService.updateTriage(id, status, note.isBlank() ? null : note);
                    return Map.of(
                            "ok", true,
                            "vulnerabilityId", updated.id(),
                            "triageStatus", updated.triageStatus().name(),
                            "triageNote", updated.triageNote() == null ? "" : updated.triageNote()
                    );
                }
        ));

        tools.add(tool(
                "finish_triage",
                "Finish the triage session with a short summary. Always call this last.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "summary", Map.of("type", "string"),
                                "healthyQueue", Map.of("type", "boolean", "description", "True if open high/critical queue is clear")
                        ),
                        "required", List.of("summary"),
                        "additionalProperties", false
                ),
                (args, ctx) -> {
                    String summary = text(args, "summary");
                    boolean healthy = args.path("healthyQueue").asBoolean(false);
                    finished = true;
                    finishSummary = summary.isBlank() ? "Triage finished." : summary;
                    return Map.of(
                            "ok", true,
                            "finished", true,
                            "healthyQueue", healthy,
                            "summary", finishSummary
                    );
                }
        ));

        return tools;
    }

    private static int severityRank(String severity) {
        if (severity == null) {
            return 0;
        }
        return switch (severity.toUpperCase(Locale.ROOT)) {
            case "CRITICAL" -> 5;
            case "HIGH" -> 4;
            case "MEDIUM" -> 3;
            case "LOW" -> 2;
            case "INFO" -> 1;
            default -> 0;
        };
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    private static String text(JsonNode args, String field) {
        if (args == null || args.isNull() || !args.has(field) || args.get(field).isNull()) {
            return "";
        }
        return args.get(field).asText("");
    }

    private OpsTool tool(String name, String description, Map<String, Object> schema, OpsToolExecutor executor) {
        return new OpsTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public Map<String, Object> parametersSchema() {
                return schema;
            }

            @Override
            public Map<String, Object> execute(JsonNode args, OpsToolContext context) {
                return executor.execute(args, context);
            }
        };
    }

    @FunctionalInterface
    private interface OpsToolExecutor {
        Map<String, Object> execute(JsonNode args, OpsToolContext context);
    }
}
