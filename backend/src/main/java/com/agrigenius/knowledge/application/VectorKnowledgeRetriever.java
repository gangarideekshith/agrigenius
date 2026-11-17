package com.agrigenius.knowledge.application;

import com.agrigenius.knowledge.config.KnowledgeProperties;
import com.agrigenius.knowledge.embedding.E5Prefixes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * pgvector similarity search over ACTIVE knowledge (retired documents have no chunks). Applies the
 * configured similarity threshold and top-k cap, and keeps the total retrieved text within
 * {@code maxContextChars} by dropping the lowest-ranked chunks. Query text is never logged.
 */
public class VectorKnowledgeRetriever implements KnowledgeRetriever {

    private static final Logger log = LoggerFactory.getLogger(VectorKnowledgeRetriever.class);

    private final VectorStore vectorStore;
    private final KnowledgeProperties properties;

    public VectorKnowledgeRetriever(VectorStore vectorStore, KnowledgeProperties properties) {
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    @Override
    public List<RetrievedChunk> retrieve(String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int k = Math.max(1, Math.min(topK, properties.topK()));
        List<Document> hits = vectorStore.similaritySearch(SearchRequest.builder()
                .query(E5Prefixes.query(query.strip()))
                .topK(k)
                .similarityThreshold(properties.similarityThreshold())
                .build());
        List<RetrievedChunk> ranked = hits.stream()
                .map(VectorKnowledgeRetriever::toChunk)
                .sorted(Comparator.comparingDouble(RetrievedChunk::similarity).reversed())
                .toList();
        List<RetrievedChunk> kept = new ArrayList<>();
        int chars = 0;
        for (RetrievedChunk chunk : ranked) {
            if (chars + chunk.content().length() > properties.maxContextChars()) {
                break;
            }
            kept.add(chunk);
            chars += chunk.content().length();
        }
        log.debug("Knowledge retrieval returned {} of {} chunks", kept.size(), hits.size());
        return List.copyOf(kept);
    }

    static RetrievedChunk toChunk(Document document) {
        Map<String, Object> m = document.getMetadata();
        return new RetrievedChunk(
                UUID.fromString(String.valueOf(m.get(ChunkMetadata.DOCUMENT_ID))),
                string(m, ChunkMetadata.TITLE),
                string(m, ChunkMetadata.PUBLISHER),
                string(m, ChunkMetadata.URL),
                string(m, ChunkMetadata.LICENCE),
                string(m, ChunkMetadata.DATA_LABEL),
                document.getText(),
                document.getScore() == null ? 0.0 : document.getScore());
    }

    private static String string(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value == null ? null : value.toString();
    }
}
