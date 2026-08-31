package com.secureai.dto;

import com.secureai.model.AnalysisJobStatus;

import java.time.Instant;

public record AnalysisJobResponse(
        String jobId,
        String repository,
        AnalysisJobStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        String errorMessage,
        Integer findingCount
) {}
