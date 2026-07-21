package com.secureai.dto;

public record ApiError(
        String message,
        int status
) {}
