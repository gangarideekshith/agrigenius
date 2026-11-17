package com.agrigenius.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import com.agrigenius.knowledge.application.KnowledgeIngestionService;
import com.agrigenius.knowledge.application.KnowledgeRetriever;
import com.agrigenius.knowledge.application.RetrievedChunk;
import com.agrigenius.knowledge.embedding.PassagePrefixingEmbeddingModel;
import com.agrigenius.support.PostgresTestcontainer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Opt-in end-to-end check with the REAL e5 model (EMBEDDING_PROVIDER=e5-onnx path, PyTorch engine
 * excluded) and pgvector. Needs the cached model (see OnnxE5RealModelCheck). Run with
 * {@code AGRIGENIUS_REAL_EMBEDDING_CHECK=true ./mvnw test -Dtest=RealEmbeddingIngestionCheck}.
 */
@EnabledIfEnvironmentVariable(named = "AGRIGENIUS_REAL_EMBEDDING_CHECK", matches = "true")
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.ai.model.embedding=e5-onnx",
        "spring.ai.vectorstore.type=pgvector",
        "agrigenius.knowledge.allow-synthetic=true",
        "agrigenius.knowledge.similarity-threshold=0.0"
})
class RealEmbeddingIngestionCheck implements PostgresTestcontainer {

    @Autowired ApplicationContext context;
    @Autowired KnowledgeIngestionService ingestion;
    @Autowired KnowledgeRetriever retriever;

    @Test
    void realModelIngestsAndRetrievesAcrossLanguages(@TempDir Path corpus) throws IOException {
        assertThat(context.getBean(EmbeddingModel.class)).isInstanceOf(PassagePrefixingEmbeddingModel.class);
        SyntheticCorpus.copyTo(corpus);
        ingestion.ingest(corpus);

        for (String query : List.of("brown spots on paddy leaves", "వరి ఆకులపై గోధుమ మచ్చలు",
                "धान की पत्तियों पर भूरे धब्बे")) {
            List<RetrievedChunk> hits = retriever.retrieve(query, 5);
            System.out.printf("%s -> %s%n", query.length() > 12 ? "[" + query.codePoints().count() + " chars]" : query,
                    hits.stream().map(h -> String.format("%s=%.4f", h.title().replace("SYNTHETIC TEST DOCUMENT – ", ""),
                            h.similarity())).toList());
            assertThat(hits.getFirst().title()).contains("paddy leaf spots");
        }
    }
}
