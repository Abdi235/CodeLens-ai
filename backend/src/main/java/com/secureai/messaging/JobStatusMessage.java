package com.secureai.messaging;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record JobStatusMessage(
        String jobId,
        String status,
        String errorMessage,
        Integer findingCount
) {}
