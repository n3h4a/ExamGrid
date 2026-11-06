package com.examgrid.backend.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * {@code examgrid.security.*}. The JWT secret must be at least 32 bytes (HS256 key size); the
 * application refuses to start without it.
 *
 * @param jwtSecret      HMAC secret for signing access tokens
 * @param issuer         {@code iss} claim
 * @param tokenTtl       access token lifetime
 * @param corsOrigins    browser origins allowed to call the API
 */
@Validated
@ConfigurationProperties("examgrid.security")
public record SecurityProperties(
        @NotBlank @Size(min = 32, message = "must be at least 32 characters") String jwtSecret,
        @NotBlank String issuer,
        @NotNull Duration tokenTtl,
        List<String> corsOrigins) {

    public SecurityProperties {
        corsOrigins = corsOrigins == null ? List.of() : List.copyOf(corsOrigins);
    }
}
