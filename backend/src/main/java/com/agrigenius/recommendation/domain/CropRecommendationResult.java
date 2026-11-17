package com.agrigenius.recommendation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One ranked crop candidate of a recommendation. */
@Entity
@Table(name = "crop_recommendation_results")
public class CropRecommendationResult {

    @Embeddable
    public static class ResultId implements Serializable {

        @Column(name = "request_id")
        private UUID requestId;

        @Column(name = "rank")
        private short rank;

        protected ResultId() {
        }

        ResultId(UUID requestId, short rank) {
            this.requestId = requestId;
            this.rank = rank;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ResultId other && rank == other.rank && Objects.equals(requestId, other.requestId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(requestId, rank);
        }
    }

    @EmbeddedId
    private ResultId id;

    @MapsId("requestId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id")
    private CropRecommendationRequest request;

    @Column(name = "crop_code", nullable = false, length = 40)
    private String cropCode;

    @Column(name = "probability", nullable = false, precision = 5, scale = 4)
    private BigDecimal probability;

    @Column(name = "suitability_score", nullable = false)
    private short suitabilityScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence_level", nullable = false, length = 10)
    private ConfidenceLevel confidenceLevel;

    /** JSON array of {feature, value, p05, p95} using backend (camelCase) feature names. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "explanation", nullable = false)
    private String explanationJson;

    protected CropRecommendationResult() {
        // for JPA
    }

    CropRecommendationResult(CropRecommendationRequest request, short rank, String cropCode, BigDecimal probability,
            short suitabilityScore, ConfidenceLevel confidenceLevel, String explanationJson) {
        this.id = new ResultId(request.getId(), rank);
        this.request = request;
        this.cropCode = cropCode;
        this.probability = probability;
        this.suitabilityScore = suitabilityScore;
        this.confidenceLevel = confidenceLevel;
        this.explanationJson = explanationJson;
    }

    public short getRank() {
        return id.rank;
    }

    public String getCropCode() {
        return cropCode;
    }

    public BigDecimal getProbability() {
        return probability;
    }

    public short getSuitabilityScore() {
        return suitabilityScore;
    }

    public ConfidenceLevel getConfidenceLevel() {
        return confidenceLevel;
    }

    public String getExplanationJson() {
        return explanationJson;
    }
}
