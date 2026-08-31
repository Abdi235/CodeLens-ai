package com.secureai.dto;

import java.time.Instant;

public record RepositoryRecordResponse(
        Long id,
        String jobId,
        String repositoryUrl,
        Integer fileCount,
        Integer indexedChunkCount,
        String primaryLanguage,
        Instant createdAt
) {}
