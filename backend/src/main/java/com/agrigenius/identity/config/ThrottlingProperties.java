package com.agrigenius.identity.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Limits for login and registration attempts (ADR-0003, decision D5). In-memory, so per instance.
 *
 * @param failedLoginsPerMobile failed logins allowed per mobile number within the window
 * @param requestsPerIp         login and registration requests allowed per client IP within the window
 * @param window                length of the fixed counting window
 */
@Validated
@ConfigurationProperties(prefix = "agrigenius.security.throttling")
public record ThrottlingProperties(
        @Min(1) int failedLoginsPerMobile,
        @Min(1) int requestsPerIp,
        @NotNull Duration window) {
}
