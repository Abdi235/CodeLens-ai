package com.secureai.dto;

import java.time.Instant;

public record ProjectResponse(
        Long id,
        String name,
        String repositoryUrl,
        Instant createdAt
) {}
