package com.agrigenius.knowledge.application;

import java.util.List;

/**
 * Finds curated knowledge relevant to a question (pgvector similarity search over ACTIVE documents).
 * Only chunks at or above the configured similarity threshold are returned, best first.
 */
public interface KnowledgeRetriever {

    List<RetrievedChunk> retrieve(String query, int topK);
}
