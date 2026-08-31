package com.secureai.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalysisJobMessage(
        String jobId,
        String repository,
        Long userId,
        int attempt
) {}
