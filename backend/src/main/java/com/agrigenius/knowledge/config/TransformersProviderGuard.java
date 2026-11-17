package com.agrigenius.knowledge.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Fails startup with a clear message if {@code EMBEDDING_PROVIDER=transformers} is set. That value would
 * activate Spring AI's TransformersEmbeddingModel, whose default model is English-only and whose pooling
 * needs DJL's PyTorch engine (excluded from the build). The supported value is {@code e5-onnx}.
 */
@Configuration
@ConditionalOnProperty(name = "spring.ai.model.embedding", havingValue = "transformers")
public class TransformersProviderGuard implements InitializingBean {

    @Override
    public void afterPropertiesSet() {
        throw new IllegalStateException("EMBEDDING_PROVIDER=transformers is not supported: use EMBEDDING_PROVIDER="
                + EmbeddingModelConfig.PROVIDER + " (multilingual e5 on ONNX Runtime, ADR-0006)");
    }
}
