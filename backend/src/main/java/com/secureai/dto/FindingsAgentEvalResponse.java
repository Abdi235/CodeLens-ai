package com.secureai.dto;

public record FindingsAgentEvalResponse(
        long evaluatedRuns,
        long successfulRuns,
        double successRatePercent,
        double avgDurationMs,
        double avgSteps
) {}
