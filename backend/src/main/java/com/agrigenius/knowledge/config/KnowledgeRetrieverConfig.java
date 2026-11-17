package com.agrigenius.knowledge.config;

import com.agrigenius.knowledge.application.KnowledgeRetriever;
import com.agrigenius.knowledge.application.NoOpKnowledgeRetriever;
import com.agrigenius.knowledge.application.VectorKnowledgeRetriever;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the retriever at startup: pgvector search when a {@link VectorStore} is configured
 * ({@code VECTOR_STORE_TYPE=pgvector} with an embedding model), otherwise a retriever that finds
 * nothing, so the chat answers {@code NO_SOURCES} instead of guessing.
 */
@Configuration
public class KnowledgeRetrieverConfig {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeRetrieverConfig.class);

    @Bean
    KnowledgeRetriever knowledgeRetriever(ObjectProvider<VectorStore> vectorStore, KnowledgeProperties properties) {
        VectorStore store = vectorStore.getIfAvailable();
        if (store == null) {
            log.info("Knowledge retrieval disabled (no vector store configured)");
            return new NoOpKnowledgeRetriever();
        }
        return new VectorKnowledgeRetriever(store, properties);
    }
}
