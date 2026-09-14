package com.secureai.dto;

import java.util.List;
import java.util.Map;

public record OpsAgentRunResponse(
        String runId,
        String mode,
        String goal,
        String status,
        String brainType,
        boolean dryRun,
        String scenario,
        Boolean success,
        String summary,
        Long durationMs,
        List<Step> steps
) {
    public record Step(
            int stepIndex,
            String role,
            String toolName,
            Map<String, Object> toolArgs,
            Map<String, Object> toolResult,
            String content
    ) {}
}
