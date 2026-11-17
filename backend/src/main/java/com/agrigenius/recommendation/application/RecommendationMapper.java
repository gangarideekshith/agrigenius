package com.agrigenius.recommendation.application;

import com.agrigenius.recommendation.api.dto.RecommendationResponse;
import com.agrigenius.recommendation.api.dto.RecommendationResponse.Candidate;
import com.agrigenius.recommendation.api.dto.RecommendationResponse.CropRef;
import com.agrigenius.recommendation.api.dto.RecommendationResponse.Economics;
import com.agrigenius.recommendation.api.dto.RecommendationResponse.Explanation;
import com.agrigenius.recommendation.api.dto.RecommendationResponse.ModelSummary;
import com.agrigenius.recommendation.api.dto.RecommendationResponse.Warning;
import com.agrigenius.recommendation.api.dto.RecommendationSummary;
import com.agrigenius.recommendation.domain.CropRecommendationRequest;
import com.agrigenius.recommendation.domain.CropRecommendationResult;
import com.agrigenius.recommendation.domain.RecommendationStatus;
import java.util.List;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Converts stored recommendations to API responses and ML feature names to backend names. */
final class RecommendationMapper {

    /** Order and names of the inputs the model uses, as exposed by the public API. */
    static final List<String> FEATURES_USED_BY_MODEL = List.of(
            "nitrogenKgHa", "phosphorusKgHa", "potassiumKgHa", "ph", "temperatureC", "humidityPct", "rainfallMm");

    private static final Map<String, String> ML_TO_API_FEATURE = Map.of(
            "nitrogen", "nitrogenKgHa",
            "phosphorus", "phosphorusKgHa",
            "potassium", "potassiumKgHa",
            "temperature_c", "temperatureC",
            "humidity_pct", "humidityPct",
            "ph", "ph",
            "rainfall_mm", "rainfallMm");

    static final String NO_ECONOMICS_REASON = "No verified market or cost data yet.";
    static final String ADVISORY = "These suggestions come from a model trained on a public sample dataset and are "
            + "not validated for your field. Confirm with your local agriculture officer or Krishi Vigyan Kendra (KVK) "
            + "before planting.";
    static final String LOW_CONFIDENCE_ADVISORY = "The model is not confident for these inputs (see warnings). "
            + "Do not rely on these suggestions: please consult your local agriculture officer or Krishi Vigyan "
            + "Kendra (KVK), and check that your soil and weather values are correct.";

    /** Stored JSON shape of one explanation entry (API feature names). */
    record StoredExplanation(String feature, double value, Double p05, Double p95) {
    }

    /** Stored JSON shape of one warning (API feature names). */
    record StoredWarning(String code, String feature) {
    }

    private final JsonMapper json;

    RecommendationMapper(JsonMapper json) {
        this.json = json;
    }

    static String apiFeatureName(String mlFeature) {
        if (mlFeature == null) {
            return null;
        }
        return ML_TO_API_FEATURE.getOrDefault(mlFeature, mlFeature);
    }

    String toJson(Object value) {
        return json.writeValueAsString(value);
    }

    RecommendationResponse toResponse(CropRecommendationRequest request, Map<String, String> cropNames) {
        List<Candidate> candidates = request.getResults().stream()
                .map(result -> toCandidate(result, cropNames))
                .toList();
        List<Warning> warnings = readWarnings(request.getWarningsJson()).stream()
                .map(w -> new Warning(w.code(), w.feature()))
                .toList();
        ModelSummary model = request.getModelName() == null ? null
                : new ModelSummary(request.getModelName(), request.getModelVersion(), request.getModelDataLabel());
        String advisory = switch (request.getStatus()) {
            case LOW_CONFIDENCE -> LOW_CONFIDENCE_ADVISORY;
            case COMPLETED -> ADVISORY;
            default -> null;
        };
        return new RecommendationResponse(request.getId(), request.getStatus().name(), request.getRequestedAt(), model,
                FEATURES_USED_BY_MODEL, candidates, warnings, advisory);
    }

    RecommendationSummary toSummary(CropRecommendationRequest request, Map<String, String> cropNames) {
        if (request.getStatus() == RecommendationStatus.FAILED || request.getResults().isEmpty()) {
            return new RecommendationSummary(request.getId(), request.getStatus().name(), request.getRequestedAt(),
                    null, null);
        }
        CropRecommendationResult top = request.getResults().get(0);
        return new RecommendationSummary(request.getId(), request.getStatus().name(), request.getRequestedAt(),
                new CropRef(top.getCropCode(), cropNames.getOrDefault(top.getCropCode(), top.getCropCode())),
                top.getConfidenceLevel().name());
    }

    private Candidate toCandidate(CropRecommendationResult result, Map<String, String> cropNames) {
        List<Explanation> explanation = readExplanations(result.getExplanationJson()).stream()
                .map(RecommendationMapper::toExplanation)
                .toList();
        return new Candidate(
                result.getRank(),
                new CropRef(result.getCropCode(), cropNames.getOrDefault(result.getCropCode(), result.getCropCode())),
                result.getSuitabilityScore(),
                result.getProbability().doubleValue(),
                result.getConfidenceLevel().name(),
                explanation,
                new Economics(false, NO_ECONOMICS_REASON));
    }

    static Explanation toExplanation(StoredExplanation stored) {
        if (stored.p05() == null || stored.p95() == null) {
            return new Explanation(stored.feature(), stored.value(), null, null);
        }
        String fit = stored.value() < stored.p05() ? "BELOW" : stored.value() > stored.p95() ? "ABOVE" : "WITHIN";
        return new Explanation(stored.feature(), stored.value(), List.of(stored.p05(), stored.p95()), fit);
    }

    private List<StoredExplanation> readExplanations(String raw) {
        return raw == null ? List.of() : json.readValue(raw, new TypeReference<List<StoredExplanation>>() { });
    }

    private List<StoredWarning> readWarnings(String raw) {
        return raw == null ? List.of() : json.readValue(raw, new TypeReference<List<StoredWarning>>() { });
    }
}
