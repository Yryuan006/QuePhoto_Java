package com.quephoto.auth.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public final class AuthDtos {
    private AuthDtos() {}

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {}

    public record LoginResponse(
            String accessToken,
            Instant expiresAt
    ) {}
}