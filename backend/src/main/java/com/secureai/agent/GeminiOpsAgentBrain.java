package com.secureai.agent;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Free-tier agentic brain: Google Gemini chooses which tools to call next.
 * Uses GEMINI_API_KEY from Google AI Studio (no paid OpenAI credits required).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiOpsAgentBrain implements OpsAgentBrain {

    private final JsonMapper objectMapper;

    @Value("${GEMINI_API_KEY:}")
    private String apiKeyFromEnv;

    @Value("${secureai.ops-agent.gemini-api-key:}")
    private String apiKeyFromConfig;

    @Value("${secureai.ops-agent.gemini-model:gemini-2.0-flash}")
    private String model;

    @Override
    public String type() {
        return "gemini-tool-calling";
    }

    public boolean isConfigured() {
        String key = resolvedKey();
        return key != null && !key.isBlank();
    }

    private String resolvedKey() {
        if (apiKeyFromConfig != null && !apiKeyFromConfig.isBlank()) {
            return apiKeyFromConfig;
        }
        return apiKeyFromEnv;
    }

    @Override
    public Decision decide(List<Map<String, Object>> messages, List<OpsTool> tools) {
        String apiKey = resolvedKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY / secureai.ops-agent.gemini-api-key is not set");
        }

        ObjectNode body = objectMapper.createObjectNode();
        String systemText = extractSystemText(messages);
        if (systemText != null && !systemText.isBlank()) {
            ObjectNode systemInstruction = body.putObject("system_instruction");
            systemInstruction.putArray("parts").addObject().put("text", systemText);
        }

        ArrayNode contents = body.putArray("contents");
        appendGeminiContents(contents, messages);

        ArrayNode functionDeclarations = objectMapper.createArrayNode();
        for (OpsTool tool : tools) {
            ObjectNode decl = functionDeclarations.addObject();
            decl.put("name", tool.name());
            decl.put("description", tool.description());
            // Gemini rejects JSON Schema keywords like additionalProperties
            decl.set("parameters", sanitizeGeminiSchema(objectMapper.valueToTree(tool.parametersSchema())));
        }
        body.putArray("tools").addObject().set("functionDeclarations", functionDeclarations);

        ObjectNode toolConfig = body.putObject("toolConfig");
        toolConfig.putObject("functionCallingConfig").put("mode", "AUTO");

        String uri = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model
                + ":generateContent?key="
                + apiKey;

        Map<?, ?> response;
        try {
            response = RestClient.create()
                    .post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Gemini request failed: " + ex.getMessage(), ex);
        }

        if (response == null) {
            throw new IllegalStateException("Empty Gemini response");
        }

        JsonNode root = objectMapper.valueToTree(response);
        JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            String err = root.path("error").path("message").asText(null);
            throw new IllegalStateException(
                    err != null ? "Gemini error: " + err : "Gemini response missing candidates/parts");
        }

        StringBuilder text = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();
        for (JsonNode part : parts) {
            if (part.has("text")) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(part.path("text").asText(""));
            }
            if (part.has("functionCall")) {
                JsonNode fc = part.path("functionCall");
                String name = fc.path("name").asText();
                JsonNode argsNode = fc.path("args");
                String argsJson = argsNode.isMissingNode() || argsNode.isNull()
                        ? "{}"
                        : argsNode.toString();
                toolCalls.add(new ToolCall("call_" + UUID.randomUUID(), name, argsJson));
            }
        }

        String content = text.isEmpty() ? null : text.toString();
        return new Decision(content, toolCalls);
    }

    /**
     * Gemini function declarations accept a subset of JSON Schema.
     * Strip unsupported keywords (e.g. additionalProperties) recursively.
     */
    private JsonNode sanitizeGeminiSchema(JsonNode node) {
        if (node == null || node.isNull()) {
            return objectMapper.createObjectNode().put("type", "object");
        }
        if (node.isObject()) {
            ObjectNode out = objectMapper.createObjectNode();
            node.properties().forEach(entry -> {
                String key = entry.getKey();
                if ("additionalProperties".equals(key)
                        || "$schema".equals(key)
                        || "$id".equals(key)
                        || "default".equals(key)) {
                    return;
                }
                out.set(key, sanitizeGeminiSchema(entry.getValue()));
            });
            if (!out.has("type") && (out.has("properties") || out.isEmpty())) {
                out.put("type", "object");
            }
            return out;
        }
        if (node.isArray()) {
            ArrayNode out = objectMapper.createArrayNode();
            for (JsonNode child : node) {
                out.add(sanitizeGeminiSchema(child));
            }
            return out;
        }
        return node;
    }

    private String extractSystemText(List<Map<String, Object>> messages) {
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> message : messages) {
            if ("system".equals(message.get("role"))) {
                Object content = message.get("content");
                if (content != null) {
                    if (!sb.isEmpty()) {
                        sb.append('\n');
                    }
                    sb.append(content);
                }
            }
        }
        return sb.toString();
    }

    /**
     * Convert OpenAI-style transcript messages into Gemini contents.
     */
    @SuppressWarnings("unchecked")
    private void appendGeminiContents(ArrayNode contents, List<Map<String, Object>> messages) {
        for (Map<String, Object> message : messages) {
            String role = String.valueOf(message.getOrDefault("role", ""));
            if ("system".equals(role)) {
                continue;
            }

            if ("user".equals(role)) {
                ObjectNode content = contents.addObject();
                content.put("role", "user");
                content.putArray("parts").addObject().put("text", String.valueOf(message.getOrDefault("content", "")));
                continue;
            }

            if ("assistant".equals(role)) {
                ObjectNode content = contents.addObject();
                content.put("role", "model");
                ArrayNode parts = content.putArray("parts");
                Object toolCallsObj = message.get("tool_calls");
                boolean wrote = false;
                if (toolCallsObj instanceof List<?> toolCalls) {
                    for (Object tcObj : toolCalls) {
                        if (!(tcObj instanceof Map<?, ?> tc)) {
                            continue;
                        }
                        Object fnObj = tc.get("function");
                        if (!(fnObj instanceof Map<?, ?> fn)) {
                            continue;
                        }
                        ObjectNode part = parts.addObject();
                        ObjectNode functionCall = part.putObject("functionCall");
                        functionCall.put("name", String.valueOf(fn.get("name")));
                        Object args = fn.get("arguments");
                        JsonNode argsNode;
                        try {
                            argsNode = objectMapper.readTree(args == null ? "{}" : String.valueOf(args));
                        } catch (Exception e) {
                            argsNode = objectMapper.createObjectNode();
                        }
                        functionCall.set("args", argsNode);
                        wrote = true;
                    }
                }
                Object contentObj = message.get("content");
                if (contentObj != null && !String.valueOf(contentObj).isBlank()) {
                    parts.addObject().put("text", String.valueOf(contentObj));
                    wrote = true;
                }
                if (!wrote) {
                    parts.addObject().put("text", "");
                }
                continue;
            }

            if ("tool".equals(role)) {
                ObjectNode content = contents.addObject();
                content.put("role", "user");
                ObjectNode part = content.putArray("parts").addObject();
                ObjectNode functionResponse = part.putObject("functionResponse");
                // Prefer tool name from prior assistant call id mapping; fall back to generic
                String name = message.containsKey("name")
                        ? String.valueOf(message.get("name"))
                        : inferToolName(messages, message);
                functionResponse.put("name", name);
                JsonNode responseNode;
                try {
                    responseNode = objectMapper.readTree(String.valueOf(message.getOrDefault("content", "{}")));
                } catch (Exception e) {
                    ObjectNode wrap = objectMapper.createObjectNode();
                    wrap.put("result", String.valueOf(message.getOrDefault("content", "")));
                    responseNode = wrap;
                }
                if (responseNode.isObject()) {
                    functionResponse.set("response", responseNode);
                } else {
                    ObjectNode wrap = objectMapper.createObjectNode();
                    wrap.set("result", responseNode);
                    functionResponse.set("response", wrap);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private String inferToolName(List<Map<String, Object>> messages, Map<String, Object> toolMessage) {
        String callId = String.valueOf(toolMessage.getOrDefault("tool_call_id", ""));
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, Object> m = messages.get(i);
            if (!"assistant".equals(m.get("role"))) {
                continue;
            }
            Object tcs = m.get("tool_calls");
            if (!(tcs instanceof List<?> list)) {
                continue;
            }
            for (Object tcObj : list) {
                if (!(tcObj instanceof Map<?, ?> tc)) {
                    continue;
                }
                if (callId.equals(String.valueOf(tc.get("id")))) {
                    Object fn = tc.get("function");
                    if (fn instanceof Map<?, ?> fnMap) {
                        return String.valueOf(fnMap.get("name"));
                    }
                }
            }
        }
        return "unknown_tool";
    }
}
