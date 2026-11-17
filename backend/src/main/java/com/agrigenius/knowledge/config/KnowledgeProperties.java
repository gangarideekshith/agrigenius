package com.agrigenius.knowledge.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Retrieval settings. The similarity threshold is provisional until tuned on the gold question set. */
@Validated
@ConfigurationProperties(prefix = "agrigenius.knowledge")
public record KnowledgeProperties(
        @Min(1) int topK,
        @DecimalMin("0.0") @DecimalMax("1.0") double similarityThreshold,
        @Min(1000) int maxContextChars,
        @NotBlank String embeddingCacheDir) {
}
