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
 * Genuine agentic brain: OpenAI chooses which tools to call next.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiOpsAgentBrain implements OpsAgentBrain {

    private final JsonMapper objectMapper;

    @Value("${OPENAI_API_KEY:}")
    private String apiKeyFromEnv;

    @Value("${secureai.ops-agent.openai-api-key:}")
    private String apiKeyFromConfig;

    @Value("${secureai.ops-agent.model:gpt-4o-mini}")
    private String model;

    @Override
    public String type() {
        return "openai-tool-calling";
    }

    public boolean isConfigured() {
        return resolvedKey() != null && !resolvedKey().isBlank();
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
            throw new IllegalStateException("OPENAI_API_KEY / secureai.ops-agent.openai-api-key is not set");
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.set("messages", objectMapper.valueToTree(messages));
        ArrayNode toolsNode = body.putArray("tools");
        for (OpsTool tool : tools) {
            ObjectNode t = toolsNode.addObject();
            t.put("type", "function");
            ObjectNode fn = t.putObject("function");
            fn.put("name", tool.name());
            fn.put("description", tool.description());
            fn.set("parameters", objectMapper.valueToTree(tool.parametersSchema()));
        }
        body.put("tool_choice", "auto");

        Map<?, ?> response = RestClient.create()
                .post()
                .uri("https://api.openai.com/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .body(body)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            throw new IllegalStateException("Empty OpenAI response");
        }
        Object choicesObj = response.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            throw new IllegalStateException("OpenAI response missing choices");
        }
        Object choice0 = choices.getFirst();
        JsonNode choice = objectMapper.valueToTree(choice0);
        JsonNode message = choice.path("message");
        String content = message.path("content").asText(null);
        List<ToolCall> toolCalls = new ArrayList<>();
        for (JsonNode tc : message.path("tool_calls")) {
            String id = tc.path("id").asText(UUID.randomUUID().toString());
            String name = tc.path("function").path("name").asText();
            String args = tc.path("function").path("arguments").asText("{}");
            toolCalls.add(new ToolCall(id, name, args));
        }
        return new Decision(content, toolCalls);
    }
}
