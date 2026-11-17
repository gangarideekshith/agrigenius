package com.agrigenius.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * Deterministic 384-dimension bag-of-words embedding for tests: each word is hashed into a dimension and
 * the vector is L2-normalised, so texts sharing words are more similar. No model, no network.
 * The e5 role words ("query", "passage") are ignored so they do not dominate the similarity.
 */
public class FakeEmbeddingModel implements EmbeddingModel {

    public static final int DIMENSIONS = 384;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> out = new ArrayList<>();
        List<String> texts = request.getInstructions();
        for (int i = 0; i < texts.size(); i++) {
            out.add(new Embedding(vector(texts.get(i)), i));
        }
        return new EmbeddingResponse(out);
    }

    @Override
    public float[] embed(Document document) {
        return vector(getEmbeddingContent(document));
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    static float[] vector(String text) {
        float[] v = new float[DIMENSIONS];
        for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (word.length() < 3 || word.equals("query") || word.equals("passage")) {
                continue;
            }
            v[Math.floorMod(word.hashCode(), DIMENSIONS)] += 1f;
        }
        double norm = 0;
        for (float x : v) {
            norm += x * x;
        }
        if (norm == 0) {
            v[0] = 1f;
            return v;
        }
        float n = (float) Math.sqrt(norm);
        for (int i = 0; i < v.length; i++) {
            v[i] /= n;
        }
        return v;
    }
}
