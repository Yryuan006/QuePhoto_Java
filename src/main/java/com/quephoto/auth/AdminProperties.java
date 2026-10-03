package com.quephoto.auth;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("admin")
public record AdminProperties(@NotBlank String username,
                              @NotBlank String passwordHash) {
}
