package com.secureai.agent;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Deterministic findings-triage policy for CI/dev when Gemini is unavailable.
 */
@Component
@RequiredArgsConstructor
public class HeuristicFindingsAgentBrain implements OpsAgentBrain {

    private final FindingsToolRegistry toolRegistry;
    private final JsonMapper objectMapper;

    @Override
    public String type() {
        return "heuristic-fallback";
    }

    @Override
    public Decision decide(List<Map<String, Object>> messages, List<OpsTool> tools) {
        if (toolRegistry.isFinished() || countTool(messages, "finish_triage") > 0) {
            return new Decision("Triage already finished.", List.of());
        }

        if (countTool(messages, "list_findings") == 0) {
            return call("list_findings", "{\"triageStatus\":\"OPEN\",\"minSeverity\":\"HIGH\"}");
        }

        JsonNode listed = lastToolResult(messages, "list_findings");
        int count = listed != null ? listed.path("count").asInt(0) : 0;
        long firstId = 0;
        String firstType = "vulnerability";
        if (listed != null && listed.path("findings").isArray() && !listed.path("findings").isEmpty()) {
            JsonNode first = listed.path("findings").get(0);
            firstId = first.path("id").asLong(0);
            firstType = first.path("type").asText("vulnerability");
        }

        if (count == 0) {
            return call("finish_triage",
                    "{\"summary\":\"No open high/critical findings. Queue is clear.\",\"healthyQueue\":true}");
        }

        if (countTool(messages, "get_finding") == 0 && firstId > 0) {
            return call("get_finding", "{\"vulnerabilityId\":" + firstId + "}");
        }

        if (countTool(messages, "search_code") == 0) {
            return call("search_code", "{\"query\":" + quote(firstType) + ",\"limit\":5}");
        }

        if (countTool(messages, "propose_fix") == 0 && firstId > 0) {
            return call("propose_fix", "{\"vulnerabilityId\":" + firstId + "}");
        }

        if (countTool(messages, "mark_triaged") == 0 && firstId > 0) {
            String goal = userGoal(messages).toLowerCase(Locale.ROOT);
            String status = goal.contains("false") ? "FALSE_POSITIVE" : "TRIAGED";
            return call("mark_triaged",
                    "{\"vulnerabilityId\":" + firstId
                            + ",\"status\":\"" + status
                            + "\",\"note\":\"Heuristic triage completed.\"}");
        }

        return call("finish_triage",
                "{\"summary\":\"Triaged top open finding with proposed fix.\",\"healthyQueue\":false}");
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
            if (!"assistant".equals(m.get("role"))) {
                continue;
            }
            Object tcs = m.get("tool_calls");
            if (tcs instanceof List<?> list) {
                for (Object tc : list) {
                    if (tc instanceof Map<?, ?> map) {
                        Object fn = map.get("function");
                        if (fn instanceof Map<?, ?> fnMap && name.equals(fnMap.get("name"))) {
                            n++;
                        } else if (name.equals(String.valueOf(map.get("name")))) {
                            n++;
                        }
                    }
                }
            }
        }
        return n;
    }

    private JsonNode lastToolResult(List<Map<String, Object>> messages, String toolName) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, Object> m = messages.get(i);
            if (!"tool".equals(m.get("role"))) {
                continue;
            }
            if (!toolName.equals(String.valueOf(m.get("name")))) {
                continue;
            }
            Object content = m.get("content");
            if (content == null) {
                return null;
            }
            try {
                return objectMapper.readTree(String.valueOf(content));
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private String userGoal(List<Map<String, Object>> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, Object> m = messages.get(i);
            if ("user".equals(m.get("role"))) {
                return String.valueOf(m.getOrDefault("content", ""));
            }
        }
        return "";
    }
}
