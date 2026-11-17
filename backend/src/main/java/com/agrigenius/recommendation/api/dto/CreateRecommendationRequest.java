package com.agrigenius.recommendation.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/**
 * Inputs for a crop recommendation. Ranges are physically plausible values, not the model's
 * training range: values outside the training range are accepted and reported as warnings.
 * Units: kg/ha for N, P and K; °C; %; mm of seasonal rainfall.
 */
public record CreateRecommendationRequest(
        @NotNull @Valid Soil soil,
        @NotNull @Valid Climate climate,
        @Pattern(regexp = "KHARIF|RABI|ZAID", message = "must be one of KHARIF, RABI, ZAID") String season,
        Boolean irrigationAvailable,
        @Size(max = 40) @KnownCropCode String previousCrop,
        @Valid Location location) {

    public CreateRecommendationRequest {
        season = season == null || season.isBlank() ? null : season.strip().toUpperCase(Locale.ROOT);
        previousCrop = previousCrop == null || previousCrop.isBlank() ? null : previousCrop.strip().toLowerCase(Locale.ROOT);
    }

    public record Soil(
            @NotNull @DecimalMin("0") @DecimalMax("300") Double nitrogenKgHa,
            @NotNull @DecimalMin("0") @DecimalMax("300") Double phosphorusKgHa,
            @NotNull @DecimalMin("0") @DecimalMax("300") Double potassiumKgHa,
            @NotNull @DecimalMin("3.0") @DecimalMax("10.0") Double ph,
            @Pattern(regexp = "SANDY|LOAMY|CLAY|SILT|BLACK|RED|LATERITE|ALLUVIAL",
                    message = "must be one of SANDY, LOAMY, CLAY, SILT, BLACK, RED, LATERITE, ALLUVIAL")
            String soilType,
            @DecimalMin("0") @DecimalMax("100") Double moisturePct) {

        public Soil {
            soilType = soilType == null || soilType.isBlank() ? null : soilType.strip().toUpperCase(Locale.ROOT);
        }
    }

    public record Climate(
            @NotNull @DecimalMin("-5") @DecimalMax("55") Double temperatureC,
            @NotNull @DecimalMin("0") @DecimalMax("100") Double humidityPct,
            @NotNull @DecimalMin("0") @DecimalMax("5000") Double rainfallMm) {
    }

    public record Location(
            @Size(max = 60) @Pattern(regexp = "[\\p{L}\\p{M} .'-]*", message = "may contain only letters, spaces and . ' -")
            String state,
            @Size(max = 60) @Pattern(regexp = "[\\p{L}\\p{M} .'-]*", message = "may contain only letters, spaces and . ' -")
            String district) {

        public Location {
            state = state == null || state.isBlank() ? null : state.strip();
            district = district == null || district.isBlank() ? null : district.strip();
        }
    }
}
