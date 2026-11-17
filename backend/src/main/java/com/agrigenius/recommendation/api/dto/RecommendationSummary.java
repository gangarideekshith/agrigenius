package com.agrigenius.recommendation.api.dto;

import java.time.Instant;
import java.util.UUID;

/** One row of the recommendation history. {@code topCrop}/{@code topConfidence} are null for failed requests. */
public record RecommendationSummary(
        UUID id,
        String status,
        Instant requestedAt,
        RecommendationResponse.CropRef topCrop,
        String topConfidence) {
}
