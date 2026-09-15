package com.secureai.dto;

import jakarta.validation.constraints.NotBlank;

public record FindingsAgentSimulateRequest(
        @NotBlank String scenario,
        boolean dryRun
) {}
