package com.secureai.dto;

import java.util.Map;

public record SystemMetricsResponse(
        long uptimeSeconds,
        long requestCount,
        long errorCount,
        double errorRatePercent,
        double avgLatencyMs,
        double p95LatencyMs,
        Map<String, String> dependencies,
        PipelineMetrics pipeline
) {
    public record PipelineMetrics(
            long queued,
            long processing,
            long completed,
            long failed,
            Double avgProcessingDurationMs
    ) {}
}
