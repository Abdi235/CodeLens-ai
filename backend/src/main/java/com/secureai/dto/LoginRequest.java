package com.secureai.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank
        @JsonAlias({"email", "username", "identifier"})
        String login,
        @NotBlank String password
) {}
