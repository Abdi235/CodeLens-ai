package com.secureai.agent;

import tools.jackson.databind.JsonNode;

import java.util.Map;

public interface OpsTool {
    String name();

    String description();

    /** JSON Schema object for OpenAI function parameters. */
    Map<String, Object> parametersSchema();

    Map<String, Object> execute(JsonNode args, OpsToolContext context);
}
