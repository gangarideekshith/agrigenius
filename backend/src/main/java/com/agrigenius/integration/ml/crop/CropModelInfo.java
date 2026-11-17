package com.agrigenius.integration.ml.crop;

/** Metadata of the currently loaded model. */
public record CropModelInfo(String name, String version, String sha256, String dataLabel,
        java.util.List<String> features, java.util.List<String> classes) {
}
