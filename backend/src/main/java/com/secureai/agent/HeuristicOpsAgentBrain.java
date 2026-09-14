package com.secureai.agent;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.secureai.dto.SystemMetricsResponse;
import com.secureai.service.MonitoringService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Deterministic fallback used when OpenAI is unavailable (local/dev/CI).
 * Not marketed as agentic — labeled heuristic-fallback. Prefer OpenAiOpsAgentBrain in prod.
 *
 * Still selects tools from observed state each turn (observe → choose → act), but the
 * selection policy is coded rather than model-decided.
 */
@Component
@RequiredArgsConstructor
public class HeuristicOpsAgentBrain implements OpsAgentBrain {

    private final MonitoringService monitoringService;
    private final OpsToolRegistry toolRegistry;
    private final JsonMapper objectMapper;

    @Override
    public String type() {
        return "heuristic-fallback";
    }

    @Override
    public Decision decide(List<Map<String, Object>> messages, List<OpsTool> tools) {
        // Count prior tool uses from the transcript
        long healthCalls = countTool(messages, "get_system_health");
        long stuckCalls = countTool(messages, "list_stuck_jobs");
        long requeueCalls = countTool(messages, "requeue_job");
        long wakeWorkerCalls = countTool(messages, "wake_worker");
        long wakeAiCalls = countTool(messages, "wake_ai");
        long pageCalls = countTool(messages, "page_human");
        long resolveCalls = countTool(messages, "resolve_incident");

        if (resolveCalls > 0 || toolRegistry.isResolved()) {
            return new Decision("Incident already resolved.", List.of());
        }

        if (healthCalls == 0) {
            return call("get_system_health", "{}");
        }

        SystemMetricsResponse health = monitoringService.snapshot();
        boolean aiDown = "DOWN".equalsIgnoreCase(health.dependencies().getOrDefault("ai", "UP"));
        boolean highErrors = health.errorRatePercent() >= 20.0 && health.requestCount() >= 5;

        if (stuckCalls == 0) {
            return call("list_stuck_jobs", "{}");
        }

        // Parse last stuck jobs result
        JsonNode lastStuck = lastToolResult(messages, "list_stuck_jobs");
        int stuckCount = lastStuck != null ? lastStuck.path("count").asInt(0) : 0;
        String firstJobId = null;
        if (lastStuck != null && lastStuck.path("jobs").isArray() && !lastStuck.path("jobs").isEmpty()) {
            firstJobId = lastStuck.path("jobs").get(0).path("jobId").asText(null);
        }

        if (stuckCount > 0 && requeueCalls == 0 && firstJobId != null) {
            return call("requeue_job", "{\"jobId\":\"" + firstJobId + "\"}");
        }

        String goal = userGoal(messages).toLowerCase(Locale.ROOT);
        boolean workerMentioned = goal.contains("worker");
        if ((stuckCount > 0 || workerMentioned) && wakeWorkerCalls == 0) {
            return call("wake_worker", "{}");
        }

        if (aiDown && wakeAiCalls == 0) {
            return call("wake_ai", "{}");
        }

        if (highErrors && pageCalls == 0) {
            return call("page_human",
                    "{\"severity\":\"high\",\"message\":\"Elevated API error rate detected by ops agent fallback.\"}");
        }

        String summary;
        if (stuckCount == 0 && !aiDown && !highErrors) {
            summary = "System healthy: no stuck jobs, dependencies OK, error rate normal.";
            return call("resolve_incident", "{\"summary\":" + quote(summary) + ",\"healthy\":true}");
        }
        summary = "Remediation attempted for observed incidents (stuck jobs / dependency / errors).";
        return call("resolve_incident", "{\"summary\":" + quote(summary) + ",\"healthy\":false}");
    }

    private Decision call(String name, String argsJson) {
        return new Decision(
                "Selecting tool " + name,
                List.of(new ToolCall("call_" + UUID.randomUUID(), name, argsJson))
        );
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private long countTool(List<Map<String, Object>> messages, String name) {
        long n = 0;
        for (Map<String, Object> m : messages) {
            if ("assistant".equals(m.get("role"))) {
                Object tcs = m.get("tool_calls");
                if (tcs instanceof List<?> list) {
                    for (Object tc : list) {
                        if (tc instanceof Map<?, ?> map) {
                            Object fn = map.get("function");
                            if (fn instanceof Map<?, ?> fnMap && name.equals(fnMap.get("name"))) {
                                n++;
                            }
                        }
                    }
                }
            }
        }
        return n;
    }

    private String userGoal(List<Map<String, Object>> messages) {
        for (Map<String, Object> m : messages) {
            if ("user".equals(m.get("role"))) {
                Object c = m.get("content");
                return c == null ? "" : String.valueOf(c);
            }
        }
        return "";
    }

    private JsonNode lastToolResult(List<Map<String, Object>> messages, String toolName) {
        String lastCallId = null;
        for (Map<String, Object> m : messages) {
            if (!"assistant".equals(m.get("role"))) {
                continue;
            }
            Object tcs = m.get("tool_calls");
            if (tcs instanceof List<?> list) {
                for (Object tc : list) {
                    if (tc instanceof Map<?, ?> map) {
                        Object fn = map.get("function");
                        if (fn instanceof Map<?, ?> fnMap && toolName.equals(fnMap.get("name"))) {
                            lastCallId = String.valueOf(map.get("id"));
                        }
                    }
                }
            }
        }
        if (lastCallId == null) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, Object> m = messages.get(i);
            if ("tool".equals(m.get("role")) && lastCallId.equals(String.valueOf(m.get("tool_call_id")))) {
                try {
                    return objectMapper.readTree(String.valueOf(m.get("content")));
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }
}
