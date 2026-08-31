package com.secureai.dto;

import java.util.List;

public record AnalysisResultsResponse(
        String jobId,
        String status,
        Integer findingCount,
        List<AnalysisFindingResponse> findings
) {}
