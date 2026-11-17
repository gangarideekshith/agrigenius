package com.agrigenius.integration.ml;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Connection settings for the private Python ML service. Server-side only: this address
 * must never be sent to the browser.
 *
 * <p>{@code apiKey} ({@code ML_SERVICE_API_KEY}, at least 32 characters) is sent as
 * {@code X-Internal-Api-Key} on inference calls (ADR-0004) and is never logged.
 */
@Validated
@ConfigurationProperties(prefix = "agrigenius.ml-service")
public record MlServiceProperties(
        @NotNull URI baseUrl,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @NotBlank @Size(min = 32, message = "must be at least 32 characters (generate one; see .env.example)")
        String apiKey) {

    @Override
    public String toString() {
        return "MlServiceProperties[baseUrl=" + baseUrl + ", connectTimeout=" + connectTimeout
                + ", readTimeout=" + readTimeout + ", apiKey=<redacted>]";
    }
}
