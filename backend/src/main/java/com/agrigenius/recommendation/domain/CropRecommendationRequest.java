package com.agrigenius.recommendation.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One crop recommendation request: the farmer's input snapshot, the model identity and the
 * ranked results. Kept even when the ML call fails (status FAILED) for traceability.
 */
@Entity
@Table(name = "crop_recommendation_requests")
public class CropRecommendationRequest {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "farm_id")
    private UUID farmId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RecommendationStatus status;

    @Column(name = "nitrogen_kg_ha", nullable = false, precision = 6, scale = 2)
    private BigDecimal nitrogenKgHa;

    @Column(name = "phosphorus_kg_ha", nullable = false, precision = 6, scale = 2)
    private BigDecimal phosphorusKgHa;

    @Column(name = "potassium_kg_ha", nullable = false, precision = 6, scale = 2)
    private BigDecimal potassiumKgHa;

    @Column(name = "soil_ph", nullable = false, precision = 4, scale = 2)
    private BigDecimal soilPh;

    @Column(name = "temperature_c", nullable = false, precision = 5, scale = 2)
    private BigDecimal temperatureC;

    @Column(name = "humidity_pct", nullable = false, precision = 5, scale = 2)
    private BigDecimal humidityPct;

    @Column(name = "rainfall_mm", nullable = false, precision = 7, scale = 2)
    private BigDecimal rainfallMm;

    @Column(name = "soil_type", length = 30)
    private String soilType;

    @Column(name = "soil_moisture_pct", precision = 5, scale = 2)
    private BigDecimal soilMoisturePct;

    @Column(name = "season", length = 10)
    private String season;

    @Column(name = "irrigation_available")
    private Boolean irrigationAvailable;

    @Column(name = "previous_crop_code", length = 40)
    private String previousCropCode;

    @Column(name = "state", length = 60)
    private String state;

    @Column(name = "district", length = 60)
    private String district;

    @Column(name = "model_name", length = 80)
    private String modelName;

    @Column(name = "model_version", length = 40)
    private String modelVersion;

    @Column(name = "model_sha256", length = 64)
    private String modelSha256;

    @Column(name = "model_data_label", length = 40)
    private String modelDataLabel;

    /** JSON array of {code, feature} warnings (backend feature names). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "warnings", nullable = false)
    private String warningsJson;

    @Column(name = "failure_code", length = 40)
    private String failureCode;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id.rank ASC")
    private List<CropRecommendationResult> results = new ArrayList<>();

    protected CropRecommendationRequest() {
        // for JPA
    }

    public CropRecommendationRequest(UUID userId, RecommendationInputs inputs, Instant requestedAt) {
        this.id = UUID.randomUUID();
        this.userId = Objects.requireNonNull(userId);
        this.status = RecommendationStatus.PENDING;
        this.nitrogenKgHa = inputs.nitrogenKgHa();
        this.phosphorusKgHa = inputs.phosphorusKgHa();
        this.potassiumKgHa = inputs.potassiumKgHa();
        this.soilPh = inputs.ph();
        this.temperatureC = inputs.temperatureC();
        this.humidityPct = inputs.humidityPct();
        this.rainfallMm = inputs.rainfallMm();
        this.soilType = inputs.soilType();
        this.soilMoisturePct = inputs.moisturePct();
        this.season = inputs.season();
        this.irrigationAvailable = inputs.irrigationAvailable();
        this.previousCropCode = inputs.previousCropCode();
        this.state = inputs.state();
        this.district = inputs.district();
        this.warningsJson = "[]";
        this.requestedAt = requestedAt;
    }

    /** Records the model identity and ranked results. */
    public void complete(RecommendationStatus finalStatus, String modelName, String modelVersion, String modelSha256,
            String modelDataLabel, String warningsJson, Instant completedAt) {
        this.status = finalStatus;
        this.modelName = modelName;
        this.modelVersion = modelVersion;
        this.modelSha256 = modelSha256;
        this.modelDataLabel = modelDataLabel;
        this.warningsJson = warningsJson;
        this.completedAt = completedAt;
    }

    public void addResult(short rank, String cropCode, BigDecimal probability, short suitabilityScore,
            ConfidenceLevel confidenceLevel, String explanationJson) {
        results.add(new CropRecommendationResult(this, rank, cropCode, probability, suitabilityScore,
                confidenceLevel, explanationJson));
    }

    public void markFailed(String failureCode, Instant completedAt) {
        this.status = RecommendationStatus.FAILED;
        this.failureCode = failureCode;
        this.completedAt = completedAt;
    }

    public RecommendationInputs inputs() {
        return new RecommendationInputs(nitrogenKgHa, phosphorusKgHa, potassiumKgHa, soilPh, temperatureC,
                humidityPct, rainfallMm, soilType, soilMoisturePct, season, irrigationAvailable, previousCropCode,
                state, district);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public RecommendationStatus getStatus() {
        return status;
    }

    public String getModelName() {
        return modelName;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public String getModelSha256() {
        return modelSha256;
    }

    public String getModelDataLabel() {
        return modelDataLabel;
    }

    public String getWarningsJson() {
        return warningsJson;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public List<CropRecommendationResult> getResults() {
        return Collections.unmodifiableList(results);
    }

    @Override
    public String toString() {
        return "CropRecommendationRequest[id=" + id + ", status=" + status + "]";
    }
}
