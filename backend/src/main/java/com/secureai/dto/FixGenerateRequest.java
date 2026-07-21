package com.secureai.dto;

import jakarta.validation.constraints.NotNull;

public record FixGenerateRequest(
        @NotNull Long vulnerabilityId,
        String codeSnippet
) {}
