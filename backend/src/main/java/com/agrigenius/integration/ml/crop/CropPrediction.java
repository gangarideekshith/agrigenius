package com.agrigenius.integration.ml.crop;

/** Ranked model output for one request, plus the model identity used for traceability. */
public record CropPrediction(ModelRef model, java.util.List<Candidate> predictions,
        java.util.List<Warning> warnings, long inferenceMs) {

    public record ModelRef(String name, String version, String sha256, String dataLabel) {
    }

    public record Candidate(String crop, double probability, java.util.List<FeatureExplanation> explanation) {
    }

    public record FeatureExplanation(String feature, double value, Double p05, Double p95) {
    }

    public record Warning(String code, String feature) {
    }
}
