package com.agrigenius.identity.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Access-token settings. The signing secret comes only from the environment ({@code JWT_SECRET})
 * and must be base64 for at least 32 bytes (256 bits, the HS256 minimum).
 * Startup fails if it is missing, too short, not base64, or a placeholder.
 */
@Validated
@ConfigurationProperties(prefix = "agrigenius.security.jwt")
public record JwtProperties(
        @NotBlank String issuer,
        @NotBlank String audience,
        @NotBlank String secret,
        @NotNull Duration accessTokenTtl) {

    static final int MIN_SECRET_BYTES = 32;

    @AssertTrue(message = "must be base64 encoding at least 32 random bytes (generate one; see .env.example)")
    public boolean isSecretStrong() {
        if (secret == null || secret.isBlank()) {
            return true; // reported by @NotBlank
        }
        try {
            return Base64.getDecoder().decode(secret.trim()).length >= MIN_SECRET_BYTES;
        } catch (IllegalArgumentException notBase64) {
            return false;
        }
    }

    @AssertTrue(message = "must be between 1 and 120 minutes")
    public boolean isAccessTokenTtlInRange() {
        return accessTokenTtl == null
                || (accessTokenTtl.compareTo(Duration.ofMinutes(1)) >= 0
                && accessTokenTtl.compareTo(Duration.ofMinutes(120)) <= 0);
    }

    /** Decoded signing key bytes. */
    public byte[] secretBytes() {
        return Base64.getDecoder().decode(secret.trim());
    }

    @Override
    public String toString() {
        return "JwtProperties[issuer=" + issuer + ", audience=" + audience + ", secret=<redacted>, accessTokenTtl="
                + accessTokenTtl + "]";
    }
}
