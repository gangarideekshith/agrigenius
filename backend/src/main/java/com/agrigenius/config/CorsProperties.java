package com.agrigenius.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Browser origins allowed to call {@code /api/**}. Bound from {@code CORS_ALLOWED_ORIGINS}
 * (comma-separated). Wildcards are rejected at startup.
 */
@Validated
@ConfigurationProperties(prefix = "agrigenius.cors")
public record CorsProperties(@NotEmpty List<String> allowedOrigins) {

    @AssertTrue(message = "must list explicit origins; '*' is not allowed")
    public boolean isWithoutWildcard() {
        return allowedOrigins == null || allowedOrigins.stream().noneMatch(o -> o.contains("*"));
    }
}
