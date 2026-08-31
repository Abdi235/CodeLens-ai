package com.secureai.dto;

import java.util.List;

public record CodeSearchResponse(
        String jobId,
        String jobStatus,
        String query,
        int resultCount,
        List<CodeSearchResultItem> results
) {}
