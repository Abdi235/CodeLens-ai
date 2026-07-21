package com.secureai.dto;

import com.secureai.model.Role;

public record AuthResponse(
        String token,
        String email,
        Role role
) {}
