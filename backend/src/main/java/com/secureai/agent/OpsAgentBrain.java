package com.secureai.agent;

import java.util.List;
import java.util.Map;

public interface OpsAgentBrain {

    String type();

    /**
     * Decide the next action(s). Return tool calls and/or a final assistant message.
     */
    Decision decide(List<Map<String, Object>> messages, List<OpsTool> tools);

    record ToolCall(String id, String name, String argumentsJson) {}

    record Decision(String assistantContent, List<ToolCall> toolCalls) {
        public boolean hasToolCalls() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }
}
