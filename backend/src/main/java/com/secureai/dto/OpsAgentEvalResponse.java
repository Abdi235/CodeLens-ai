package com.secureai.dto;

public record OpsAgentEvalResponse(
        long evaluatedRuns,
        long successfulRuns,
        double successRatePercent,
        double avgDurationMs,
        double avgSteps
) {}
