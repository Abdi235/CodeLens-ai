package com.secureai.dto;

import com.secureai.model.ScanStatus;

import java.time.Instant;

public record ScanResponse(
        Long id,
        Long projectId,
        ScanStatus status,
        Instant startedAt,
        Instant completedAt,
        Integer vulnerabilityCount
) {}
