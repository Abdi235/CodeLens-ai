package com.secureai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OpsAgentRunRequest(
        @NotBlank @Size(max = 2000) String goal,
        boolean dryRun
) {}
