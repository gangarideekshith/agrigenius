package com.agrigenius.recommendation.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Confidence thresholds (decision Q8) and the per-user request limit.
 * A candidate is HIGH at or above {@code high}, MEDIUM at or above {@code medium}, otherwise LOW.
 */
@Validated
@ConfigurationProperties(prefix = "agrigenius.recommendation")
public record RecommendationProperties(@NotNull @Valid Confidence confidence, @NotNull @Valid Throttle throttle) {

    public record Confidence(double high, double medium) {

        @AssertTrue(message = "must satisfy 0 < medium < high <= 1")
        public boolean isOrdered() {
            return medium > 0 && medium < high && high <= 1;
        }
    }

    public record Throttle(@Min(1) int perUserPerHour) {
    }
}
