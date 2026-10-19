package com.parking.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Initial administrator account, created on startup if it does not exist.
 */
@Validated
@ConfigurationProperties(prefix = "app.admin")
public record AdminAccountProperties(
        @NotBlank String name,
        @NotBlank String email,
        @NotBlank String password
) {
}
