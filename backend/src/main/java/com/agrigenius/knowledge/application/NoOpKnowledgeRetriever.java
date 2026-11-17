package com.agrigenius.knowledge.application;

import java.util.List;

/** Used when no vector store is configured: retrieval finds nothing, so answers fall back to NO_SOURCES. */
public class NoOpKnowledgeRetriever implements KnowledgeRetriever {

    @Override
    public List<RetrievedChunk> retrieve(String query, int topK) {
        return List.of();
    }
}
