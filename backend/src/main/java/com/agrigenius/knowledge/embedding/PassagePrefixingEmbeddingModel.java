package com.agrigenius.knowledge.embedding;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.DisposableBean;

/**
 * Wraps an embedding model so that {@link Document}s (what the vector store embeds when chunks are
 * added) are embedded as e5 passages, while the stored chunk text stays unprefixed. Plain strings
 * pass through unchanged; the retriever adds the {@code "query: "} prefix to search text itself.
 */
public class PassagePrefixingEmbeddingModel implements EmbeddingModel, DisposableBean {

    private final EmbeddingModel delegate;

    public PassagePrefixingEmbeddingModel(EmbeddingModel delegate) {
        this.delegate = delegate;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        return delegate.call(request);
    }

    @Override
    public float[] embed(Document document) {
        return delegate.embed(getEmbeddingContent(document));
    }

    @Override
    public String getEmbeddingContent(Document document) {
        return E5Prefixes.passage(document.getText());
    }

    @Override
    public int dimensions() {
        return delegate.dimensions();
    }

    @Override
    public void destroy() throws Exception {
        if (delegate instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }
}
