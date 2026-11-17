package com.agrigenius.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agrigenius.knowledge.application.KnowledgeIngestionService;
import com.agrigenius.knowledge.application.KnowledgeRetriever;
import com.agrigenius.knowledge.application.NoOpKnowledgeRetriever;
import com.agrigenius.support.PostgresTestcontainer;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/** With the defaults (no embedding model, vector store type none) the app starts and retrieval finds nothing. */
@SpringBootTest
@ActiveProfiles("test")
class KnowledgeDisabledContextTest implements PostgresTestcontainer {

    @Autowired ApplicationContext context;
    @Autowired KnowledgeRetriever retriever;
    @Autowired KnowledgeIngestionService ingestion;

    @Test
    void retrievalIsANoOpAndIngestionExplainsWhatToEnable() {
        assertThat(context.getBeanNamesForType(VectorStore.class)).isEmpty();
        assertThat(context.getBeanNamesForType(EmbeddingModel.class)).isEmpty();
        assertThat(retriever).isInstanceOf(NoOpKnowledgeRetriever.class);
        assertThat(retriever.retrieve("anything", 5)).isEmpty();
        assertThatThrownBy(() -> ingestion.ingest(Path.of("unused")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VECTOR_STORE_TYPE=pgvector");
    }
}
