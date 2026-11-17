package com.agrigenius.knowledge.embedding;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * intfloat/multilingual-e5-small on ONNX Runtime, in process (ADR-0006, decision C2): farmer text is
 * never sent anywhere for embedding.
 *
 * <p>Pooling is done here in plain Java (attention-masked mean of {@code last_hidden_state}, then L2
 * normalisation, as the model card specifies). This replaces Spring AI's TransformersEmbeddingModel,
 * whose pooling needs DJL's PyTorch engine and a runtime libtorch download. The caller is responsible
 * for the e5 {@code "query: "}/{@code "passage: "} prefixes (see {@link E5Prefixes}).
 */
public class OnnxE5EmbeddingModel extends AbstractEmbeddingModel implements AutoCloseable {

    public static final int DIMENSIONS = 384;
    static final int MAX_TOKENS = 512;
    static final int BATCH_SIZE = 16;
    static final String OUTPUT_NAME = "last_hidden_state";

    private final OrtEnvironment environment;
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;
    private final Set<String> inputNames;

    public OnnxE5EmbeddingModel(Path modelFile, Path tokenizerFile) throws OrtException, IOException {
        this.environment = OrtEnvironment.getEnvironment();
        this.session = environment.createSession(modelFile.toString(), new OrtSession.SessionOptions());
        this.tokenizer = HuggingFaceTokenizer.newInstance(tokenizerFile, Map.of(
                "padding", "true",
                "truncation", "true",
                "maxLength", Integer.toString(MAX_TOKENS)));
        this.inputNames = session.getInputNames();
        if (!session.getOutputNames().contains(OUTPUT_NAME)) {
            throw new IllegalStateException("ONNX model has no '" + OUTPUT_NAME + "' output");
        }
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<float[]> vectors = embedTexts(request.getInstructions());
        List<Embedding> embeddings = new ArrayList<>(vectors.size());
        for (int i = 0; i < vectors.size(); i++) {
            embeddings.add(new Embedding(vectors.get(i), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return embedTexts(List.of(getEmbeddingContent(document))).getFirst();
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    List<float[]> embedTexts(List<String> texts) {
        List<float[]> result = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += BATCH_SIZE) {
            result.addAll(embedBatch(texts.subList(start, Math.min(start + BATCH_SIZE, texts.size()))));
        }
        return result;
    }

    private List<float[]> embedBatch(List<String> texts) {
        Encoding[] encodings = tokenizer.batchEncode(texts);
        int batch = encodings.length;
        int length = encodings[0].getIds().length;
        long[][] ids = new long[batch][];
        long[][] mask = new long[batch][];
        long[][] types = new long[batch][];
        for (int i = 0; i < batch; i++) {
            ids[i] = encodings[i].getIds();
            mask[i] = encodings[i].getAttentionMask();
            types[i] = encodings[i].getTypeIds();
        }
        Map<String, OnnxTensor> inputs = new HashMap<>();
        try {
            putIfExpected(inputs, "input_ids", ids);
            putIfExpected(inputs, "attention_mask", mask);
            putIfExpected(inputs, "token_type_ids", types);
            try (OrtSession.Result output = session.run(inputs)) {
                OnnxValue hidden = output.get(OUTPUT_NAME)
                        .orElseThrow(() -> new IllegalStateException("missing " + OUTPUT_NAME));
                float[][][] states = (float[][][]) hidden.getValue();
                List<float[]> vectors = new ArrayList<>(batch);
                for (int i = 0; i < batch; i++) {
                    vectors.add(normalize(meanPool(states[i], mask[i], length)));
                }
                return vectors;
            }
        } catch (OrtException e) {
            throw new IllegalStateException("Embedding inference failed", e);
        } finally {
            inputs.values().forEach(OnnxTensor::close);
        }
    }

    private void putIfExpected(Map<String, OnnxTensor> inputs, String name, long[][] values) throws OrtException {
        if (inputNames.contains(name)) {
            inputs.put(name, OnnxTensor.createTensor(environment, values));
        }
    }

    /** Mean of token vectors where the attention mask is 1. */
    static float[] meanPool(float[][] tokenStates, long[] attentionMask, int length) {
        int dims = tokenStates[0].length;
        float[] sum = new float[dims];
        int count = 0;
        for (int t = 0; t < length; t++) {
            if (attentionMask[t] == 0) {
                continue;
            }
            count++;
            for (int d = 0; d < dims; d++) {
                sum[d] += tokenStates[t][d];
            }
        }
        if (count > 0) {
            for (int d = 0; d < dims; d++) {
                sum[d] /= count;
            }
        }
        return sum;
    }

    /** Scales to unit length (no-op for the zero vector). */
    static float[] normalize(float[] vector) {
        double norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        if (norm == 0) {
            return vector;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / norm);
        }
        return vector;
    }

    @Override
    public void close() throws OrtException {
        tokenizer.close();
        session.close();
    }
}
