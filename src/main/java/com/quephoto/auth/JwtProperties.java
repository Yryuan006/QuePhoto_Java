package com.quephoto.auth;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("jwt")
public record JwtProperties(@NotBlank String secret,
                            @NotBlank String issuer,
                            @NotBlank String audience) {
}
