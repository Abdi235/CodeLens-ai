package com.secureai.dto;

public record FixGenerateResponse(
        Long vulnerabilityId,
        String before,
        String after,
        String explanation
) {}
