package com.agrigenius.recommendation.domain;

import java.math.BigDecimal;

/** Snapshot of everything the farmer submitted (values already rounded to the stored scale). */
public record RecommendationInputs(
        BigDecimal nitrogenKgHa,
        BigDecimal phosphorusKgHa,
        BigDecimal potassiumKgHa,
        BigDecimal ph,
        BigDecimal temperatureC,
        BigDecimal humidityPct,
        BigDecimal rainfallMm,
        String soilType,
        BigDecimal moisturePct,
        String season,
        Boolean irrigationAvailable,
        String previousCropCode,
        String state,
        String district) {
}
