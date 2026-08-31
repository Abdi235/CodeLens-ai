package com.secureai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnalysisCreateRequest(
        @NotBlank @Size(max = 2048) String repository
) {}
