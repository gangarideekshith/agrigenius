package com.agrigenius.integration.ml.crop;

/** The seven model inputs, in the units the model was trained on. */
public record CropFeatures(double nitrogen, double phosphorus, double potassium, double temperatureC,
        double humidityPct, double ph, double rainfallMm) {
}
