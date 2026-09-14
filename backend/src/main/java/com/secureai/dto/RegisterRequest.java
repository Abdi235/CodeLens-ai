package com.secureai.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Size(max = 255) String email,
        @JsonAlias({"userName", "user_name"})
        @Size(min = 3, max = 32) String username,
        @NotBlank @Size(min = 8, max = 100) String password
) {}
