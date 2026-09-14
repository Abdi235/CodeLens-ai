package com.secureai.dto;

import jakarta.validation.constraints.NotBlank;

public record OpsAgentSimulateRequest(
        @NotBlank String scenario,
        boolean dryRun
) {}
