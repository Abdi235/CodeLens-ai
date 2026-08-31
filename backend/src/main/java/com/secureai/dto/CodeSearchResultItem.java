package com.secureai.dto;

public record CodeSearchResultItem(
        String filePath,
        Integer startLine,
        Integer endLine,
        String language,
        String snippet,
        double score
) {}
