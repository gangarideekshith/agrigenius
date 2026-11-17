package com.agrigenius.knowledge.config;

import com.agrigenius.knowledge.embedding.ModelFileCache;
import com.agrigenius.knowledge.embedding.OnnxE5EmbeddingModel;
import com.agrigenius.knowledge.embedding.PassagePrefixingEmbeddingModel;
import java.net.URI;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * In-process multilingual embeddings, active only when {@code EMBEDDING_PROVIDER=e5-onnx}
 * ({@code spring.ai.model.embedding=e5-onnx}). A distinct value keeps Spring AI's own
 * TransformersEmbeddingModel auto-configuration (English-only default model, needs DJL's excluded
 * PyTorch engine) inactive; {@link TransformersProviderGuard} rejects {@code transformers} explicitly.
 *
 * <p>Defaults pin intfloat/multilingual-e5-small (MIT licence) to an exact HuggingFace commit and
 * verify both files' SHA-256 before loading. {@code spring.ai.embedding.transformer.onnx.model-uri} and
 * {@code spring.ai.embedding.transformer.tokenizer.uri} override the sources (for example a local
 * {@code file:} mirror); overridden files are not checksum-verified unless the matching
 * {@code agrigenius.knowledge.embedding-model-sha256} / {@code ...embedding-tokenizer-sha256} is set.
 */
@Configuration
@ConditionalOnProperty(name = "spring.ai.model.embedding", havingValue = EmbeddingModelConfig.PROVIDER)
public class EmbeddingModelConfig {

    public static final String PROVIDER = "e5-onnx";

    private static final Logger log = LoggerFactory.getLogger(EmbeddingModelConfig.class);

    static final String MODEL_COMMIT = "614241f622f53c4eeff9890bdc4f31cfecc418b3";
    static final String DEFAULT_MODEL_URI =
            "https://huggingface.co/intfloat/multilingual-e5-small/resolve/" + MODEL_COMMIT + "/onnx/model.onnx";
    static final String DEFAULT_MODEL_SHA256 = "ca456c06b3a9505ddfd9131408916dd79290368331e7d76bb621f1cba6bc8665";
    static final String DEFAULT_TOKENIZER_URI =
            "https://huggingface.co/intfloat/multilingual-e5-small/resolve/" + MODEL_COMMIT + "/onnx/tokenizer.json";
    static final String DEFAULT_TOKENIZER_SHA256 = "0b44a9d7b51c3c62626640cda0e2c2f70fdacdc25bbbd68038369d14ebdf4c39";

    @Bean
    EmbeddingModel embeddingModel(Environment environment, KnowledgeProperties properties) throws Exception {
        String modelUri = environment.getProperty("spring.ai.embedding.transformer.onnx.model-uri");
        String tokenizerUri = environment.getProperty("spring.ai.embedding.transformer.tokenizer.uri");
        String modelSha = modelUri == null ? DEFAULT_MODEL_SHA256
                : environment.getProperty("agrigenius.knowledge.embedding-model-sha256");
        String tokenizerSha = tokenizerUri == null ? DEFAULT_TOKENIZER_SHA256
                : environment.getProperty("agrigenius.knowledge.embedding-tokenizer-sha256");
        if ((modelUri != null && modelSha == null) || (tokenizerUri != null && tokenizerSha == null)) {
            log.warn("Custom embedding model source configured without a SHA-256; the file is not verified");
        }
        Path cacheDir = Path.of(properties.embeddingCacheDir()).toAbsolutePath()
                .resolve("multilingual-e5-small-" + (modelUri == null ? MODEL_COMMIT.substring(0, 12) : "custom"));
        Path model = ModelFileCache.fetch(URI.create(modelUri == null ? DEFAULT_MODEL_URI : modelUri),
                modelSha, cacheDir, "model.onnx");
        Path tokenizer = ModelFileCache.fetch(URI.create(tokenizerUri == null ? DEFAULT_TOKENIZER_URI : tokenizerUri),
                tokenizerSha, cacheDir, "tokenizer.json");
        return new PassagePrefixingEmbeddingModel(new OnnxE5EmbeddingModel(model, tokenizer));
    }
}
