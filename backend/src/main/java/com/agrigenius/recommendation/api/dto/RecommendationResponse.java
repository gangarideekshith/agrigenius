package com.agrigenius.recommendation.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A crop recommendation. Agronomic suitability only: economics are reported as unavailable
 * until verified market and cost data exist (decision Q5).
 */
public record RecommendationResponse(
        UUID id,
        String status,
        Instant requestedAt,
        ModelSummary model,
        List<String> featuresUsedByModel,
        List<Candidate> candidates,
        List<Warning> warnings,
        String advisory) {

    public record ModelSummary(String name, String version, String dataLabel) {
    }

    public record CropRef(String code, String name) {
    }

    public record Candidate(
            int rank,
            CropRef crop,
            int suitabilityScore,
            double probability,
            String confidence,
            List<Explanation> explanation,
            Economics economics) {
    }

    /** Compares the farmer's value with the 5th–95th percentile seen for this crop in training data. */
    public record Explanation(String feature, double value, List<Double> typicalRange, String fit) {
    }

    public record Economics(boolean available, String reason) {
    }

    public record Warning(String code, String feature) {
    }
}
