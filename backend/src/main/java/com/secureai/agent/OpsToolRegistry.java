package com.secureai.agent;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.secureai.dto.SystemMetricsResponse;
import com.secureai.model.AnalysisJob;
import com.secureai.service.AiServiceClient;
import com.secureai.service.AnalysisJobService;
import com.secureai.service.MonitoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpsToolRegistry {

    private final MonitoringService monitoringService;
    private final AnalysisJobService analysisJobService;
    private final AiServiceClient aiServiceClient;
    private final JsonMapper objectMapper;

    @Value("${secureai.worker-url:}")
    private String workerUrl;

    @Value("${secureai.ai-service.url:http://localhost:8000}")
    private String aiServiceUrl;

    @Value("${secureai.ops-agent.stuck-job-minutes:2}")
    private long stuckJobMinutes;

    private final List<Map<String, Object>> humanPages = new CopyOnWriteArrayList<>();
    private volatile boolean resolved;
    private volatile String resolveSummary = "";

    public List<OpsTool> tools() {
        List<OpsTool> tools = new ArrayList<>();
        tools.add(tool(
                "get_system_health",
                "Read live CodeLens service health: uptime, error rate, latency, dependency status, and analysis pipeline counts.",
                Map.of("type", "object", "properties", Map.of(), "additionalProperties", false),
                (args, ctx) -> {
                    SystemMetricsResponse snap = monitoringService.snapshot();
                    Map<String, Object> out = objectMapper.convertValue(snap, Map.class);
                    out.put("observedAt", Instant.now().toString());
                    return out;
                }
        ));
        tools.add(tool(
                "list_stuck_jobs",
                "List analysis jobs stuck in QUEUED or PROCESSING longer than the stuck threshold.",
                Map.of("type", "object", "properties", Map.of(), "additionalProperties", false),
                (args, ctx) -> {
                    Instant cutoff = Instant.now().minus(stuckJobMinutes, ChronoUnit.MINUTES);
                    List<AnalysisJob> stuck = analysisJobService.findStuckJobs(cutoff);
                    List<Map<String, Object>> items = new ArrayList<>();
                    for (AnalysisJob job : stuck) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("jobId", job.getJobId());
                        row.put("status", job.getStatus().name());
                        row.put("repository", job.getRepository());
                        row.put("createdAt", job.getCreatedAt() != null ? job.getCreatedAt().toString() : null);
                        row.put("startedAt", job.getStartedAt() != null ? job.getStartedAt().toString() : null);
                        row.put("workerId", job.getWorkerId());
                        row.put("errorMessage", job.getErrorMessage());
                        items.add(row);
                    }
                    return Map.of(
                            "stuckThresholdMinutes", stuckJobMinutes,
                            "count", items.size(),
                            "jobs", items
                    );
                }
        ));
        tools.add(tool(
                "requeue_job",
                "Requeue a stuck analysis job: reset to QUEUED and republish to CloudAMQP so a worker can pick it up.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "jobId", Map.of("type", "string", "description", "Analysis job UUID to requeue")
                        ),
                        "required", List.of("jobId"),
                        "additionalProperties", false
                ),
                (args, ctx) -> {
                    String jobId = text(args, "jobId");
                    if (jobId.isBlank()) {
                        return Map.of("ok", false, "error", "jobId is required");
                    }
                    if (ctx.dryRun()) {
                        return Map.of("ok", true, "dryRun", true, "action", "requeue_job", "jobId", jobId);
                    }
                    var job = analysisJobService.requeueJob(jobId);
                    return Map.of(
                            "ok", true,
                            "jobId", job.jobId(),
                            "status", job.status().name(),
                            "repository", job.repository()
                    );
                }
        ));
        tools.add(tool(
                "wake_worker",
                "Wake the analysis worker by calling its /health endpoint (useful after free-tier sleep).",
                Map.of("type", "object", "properties", Map.of(), "additionalProperties", false),
                (args, ctx) -> wake(ctx, "worker", resolveWorkerUrl())
        ));
        tools.add(tool(
                "wake_ai",
                "Wake the AI service by calling its /health endpoint.",
                Map.of("type", "object", "properties", Map.of(), "additionalProperties", false),
                (args, ctx) -> wake(ctx, "ai", trimSlash(aiServiceUrl) + "/health")
        ));
        tools.add(tool(
                "wake_api",
                "Confirm API liveness via local monitoring snapshot (API is already handling this request).",
                Map.of("type", "object", "properties", Map.of(), "additionalProperties", false),
                (args, ctx) -> Map.of(
                        "ok", true,
                        "service", "api",
                        "status", "UP",
                        "note", "API process is live because it served this agent request"
                )
        ));
        tools.add(tool(
                "page_human",
                "Page a human operator with a severity and message when automated remediation is insufficient.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "severity", Map.of("type", "string", "description", "low|medium|high|critical"),
                                "message", Map.of("type", "string", "description", "What the human should look at")
                        ),
                        "required", List.of("message"),
                        "additionalProperties", false
                ),
                (args, ctx) -> {
                    String severity = text(args, "severity");
                    if (severity.isBlank()) {
                        severity = "medium";
                    }
                    String message = text(args, "message");
                    Map<String, Object> page = new LinkedHashMap<>();
                    page.put("severity", severity);
                    page.put("message", message);
                    page.put("at", Instant.now().toString());
                    page.put("runId", ctx.runId());
                    page.put("dryRun", ctx.dryRun());
                    humanPages.add(page);
                    log.warn("Ops agent paged human severity={} message={}", severity, message);
                    return Map.of("ok", true, "paged", true, "severity", severity, "message", message);
                }
        ));
        tools.add(tool(
                "resolve_incident",
                "Mark the incident resolved (or healthy/no-op) and stop the agent loop. Always call this when done.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "summary", Map.of("type", "string", "description", "Final resolution summary"),
                                "healthy", Map.of("type", "boolean", "description", "True if system is healthy / no action needed")
                        ),
                        "required", List.of("summary"),
                        "additionalProperties", false
                ),
                (args, ctx) -> {
                    resolveSummary = text(args, "summary");
                    resolved = true;
                    boolean healthy = args != null && args.path("healthy").asBoolean(false);
                    return Map.of(
                            "ok", true,
                            "resolved", true,
                            "healthy", healthy,
                            "summary", resolveSummary
                    );
                }
        ));
        return tools;
    }

    public OpsTool require(String name) {
        return tools().stream()
                .filter(t -> t.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + name));
    }

    public boolean isResolved() {
        return resolved;
    }

    public String getResolveSummary() {
        return resolveSummary;
    }

    public void resetRunState() {
        resolved = false;
        resolveSummary = "";
    }

    public List<Map<String, Object>> recentPages() {
        return List.copyOf(humanPages);
    }

    public boolean isAiHealthy() {
        return aiServiceClient.isHealthy();
    }

    private Map<String, Object> wake(OpsToolContext ctx, String service, String url) {
        if (url == null || url.isBlank()) {
            return Map.of("ok", false, "service", service, "error", "URL not configured");
        }
        if (ctx.dryRun()) {
            return Map.of("ok", true, "dryRun", true, "service", service, "url", url);
        }
        try {
            String body = RestClient.create().get().uri(url).retrieve().body(String.class);
            return Map.of(
                    "ok", true,
                    "service", service,
                    "url", url,
                    "bodyPreview", body == null ? "" : body.substring(0, Math.min(body.length(), 200))
            );
        } catch (Exception e) {
            return Map.of("ok", false, "service", service, "url", url, "error", e.getMessage());
        }
    }

    private String resolveWorkerUrl() {
        String base = workerUrl == null ? "" : workerUrl.trim();
        if (base.isBlank()) {
            return "";
        }
        if (base.endsWith("/health")) {
            return base;
        }
        return trimSlash(base) + "/health";
    }

    private static String trimSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
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
