package com.agrigenius.knowledge.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Opt-in check against the real multilingual-e5-small model (downloads ~470 MB once, approved under
 * decision C2). Not run in CI. Run with:
 * {@code AGRIGENIUS_REAL_EMBEDDING_CHECK=true ./mvnw test -Dtest=OnnxE5RealModelCheck}
 * (class name does not end in "Test", so the default test run skips it even when the variable is set).
 */
@EnabledIfEnvironmentVariable(named = "AGRIGENIUS_REAL_EMBEDDING_CHECK", matches = "true")
class OnnxE5RealModelCheck {

    private static final String COMMIT = "614241f622f53c4eeff9890bdc4f31cfecc418b3";

    @Test
    void realModelHas384DimensionsAndMatchesAcrossLanguages() throws Exception {
        Path cache = Path.of(System.getProperty("user.home"), ".agrigenius", "embedding-models",
                "multilingual-e5-small-" + COMMIT.substring(0, 12));
        long started = System.nanoTime();
        Path model = ModelFileCache.fetch(java.net.URI.create(
                "https://huggingface.co/intfloat/multilingual-e5-small/resolve/" + COMMIT + "/onnx/model.onnx"),
                "ca456c06b3a9505ddfd9131408916dd79290368331e7d76bb621f1cba6bc8665", cache, "model.onnx");
        Path tokenizer = ModelFileCache.fetch(java.net.URI.create(
                "https://huggingface.co/intfloat/multilingual-e5-small/resolve/" + COMMIT + "/onnx/tokenizer.json"),
                "0b44a9d7b51c3c62626640cda0e2c2f70fdacdc25bbbd68038369d14ebdf4c39", cache, "tokenizer.json");
        System.out.printf("model ready in %d s%n", (System.nanoTime() - started) / 1_000_000_000);

        try (OnnxE5EmbeddingModel e5 = new OnnxE5EmbeddingModel(model, tokenizer)) {
            String ricePests = E5Prefixes.passage("Brown spots on rice leaves are often caused by the fungal disease "
                    + "brown spot; inspect fields and consult the local agriculture officer.");
            String unrelated = E5Prefixes.passage("Tractor tyres should be inflated to the pressure printed on the "
                    + "sidewall before road transport.");
            String teluguQuery = E5Prefixes.query("వరి ఆకులపై గోధుమ రంగు మచ్చలు ఎందుకు వస్తాయి?");
            String hindiQuery = E5Prefixes.query("धान की पत्तियों पर भूरे धब्बे क्यों आते हैं?");

            List<float[]> v = e5.embedTexts(List.of(ricePests, unrelated, teluguQuery, hindiQuery));
            assertThat(v.getFirst()).hasSize(384);

            double teRice = cosine(v.get(2), v.get(0));
            double teOther = cosine(v.get(2), v.get(1));
            double hiRice = cosine(v.get(3), v.get(0));
            double hiOther = cosine(v.get(3), v.get(1));
            System.out.printf("te->rice %.4f te->unrelated %.4f | hi->rice %.4f hi->unrelated %.4f%n",
                    teRice, teOther, hiRice, hiOther);
            assertThat(teRice).isGreaterThan(teOther);
            assertThat(hiRice).isGreaterThan(hiOther);
        }
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return dot / Math.sqrt(na * nb);
    }
}
