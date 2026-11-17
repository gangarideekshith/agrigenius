package com.agrigenius.knowledge.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

class EmbeddingComponentsTest {

    /** Records the texts it is asked to embed. */
    static final class RecordingModel implements EmbeddingModel {
        final List<String> seen = new ArrayList<>();

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            seen.addAll(request.getInstructions());
            return new EmbeddingResponse(request.getInstructions().stream().map(t -> new Embedding(new float[] {1f}, 0)).toList());
        }

        @Override
        public float[] embed(Document document) {
            seen.add(document.getText());
            return new float[] {1f};
        }
    }

    @Test
    void prefixesFollowE5Convention() {
        assertThat(E5Prefixes.query("rice")).isEqualTo("query: rice");
        assertThat(E5Prefixes.passage("rice")).isEqualTo("passage: rice");
    }

    @Test
    void documentsAreEmbeddedAsPassagesButStringsPassThrough() {
        RecordingModel delegate = new RecordingModel();
        PassagePrefixingEmbeddingModel model = new PassagePrefixingEmbeddingModel(delegate);

        model.embed(List.of(new Document("chunk text")), null, new org.springframework.ai.embedding.TokenCountBatchingStrategy());
        model.embed("query: what pest");

        assertThat(delegate.seen).containsExactly("passage: chunk text", "query: what pest");
        assertThat(new Document("chunk text").getText()).isEqualTo("chunk text");
    }

    @Test
    void meanPoolingIgnoresPaddedTokensAndVectorsAreUnitLength() {
        float[][] states = {{1f, 3f}, {3f, 5f}, {100f, 100f}};
        long[] mask = {1, 1, 0};

        float[] pooled = OnnxE5EmbeddingModel.meanPool(states, mask, 3);
        assertThat(pooled).containsExactly(2f, 4f);

        float[] unit = OnnxE5EmbeddingModel.normalize(pooled);
        assertThat(Math.sqrt(unit[0] * unit[0] + unit[1] * unit[1])).isCloseTo(1.0, within(1e-6));
        assertThat(OnnxE5EmbeddingModel.normalize(new float[] {0f, 0f})).containsExactly(0f, 0f);
    }

    @Test
    void cachedFileIsReusedOnlyWhenItsChecksumMatches(@TempDir Path dir) throws IOException {
        Path source = dir.resolve("source.bin");
        Files.writeString(source, "model bytes");
        String sha = ModelFileCache.sha256(source);
        Path cache = dir.resolve("cache");

        Path fetched = ModelFileCache.fetch(source.toUri(), sha, cache, "model.onnx");
        assertThat(Files.readString(fetched)).isEqualTo("model bytes");

        Files.writeString(fetched, "tampered");
        Path refetched = ModelFileCache.fetch(source.toUri(), sha, cache, "model.onnx");
        assertThat(Files.readString(refetched)).isEqualTo("model bytes");
    }

    @Test
    void downloadWithWrongChecksumIsRejectedAndRemoved(@TempDir Path dir) throws IOException {
        Path source = dir.resolve("source.bin");
        Files.writeString(source, "model bytes");
        Path cache = dir.resolve("cache");

        assertThatThrownBy(() -> ModelFileCache.fetch(source.toUri(), "0".repeat(64), cache, "model.onnx"))
                .isInstanceOf(IOException.class).hasMessageContaining("Checksum mismatch");
        assertThat(cache.resolve("model.onnx")).doesNotExist();
        assertThat(cache.resolve("model.onnx.part")).doesNotExist();
    }
}
