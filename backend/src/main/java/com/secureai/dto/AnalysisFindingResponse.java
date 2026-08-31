package com.secureai.dto;

import com.secureai.model.Severity;

public record AnalysisFindingResponse(
        Long id,
        String vulnerabilityType,
        String normalizedType,
        Severity severity,
        String filePath,
        Integer lineNumber,
        String description,
        String remediation,
        String retrievedContext,
        String aiExplanation,
        Double classificationConfidence,
        String ruleId
) {}
