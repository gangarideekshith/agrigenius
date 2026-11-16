package com.agrigenius.integration.ml;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Connection settings for the private Python ML service. Server-side only: this address
 * must never be sent to the browser.
 */
@Validated
@ConfigurationProperties(prefix = "agrigenius.ml-service")
public record MlServiceProperties(
        @NotNull URI baseUrl,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout) {
}
