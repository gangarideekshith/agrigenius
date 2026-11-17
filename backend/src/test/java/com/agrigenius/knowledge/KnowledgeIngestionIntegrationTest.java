package com.agrigenius.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agrigenius.knowledge.application.KnowledgeIngestionService;
import com.agrigenius.knowledge.application.KnowledgeIngestionService.IngestionReport;
import com.agrigenius.knowledge.application.KnowledgeManifestException;
import com.agrigenius.knowledge.application.KnowledgeRetriever;
import com.agrigenius.knowledge.application.RetrievedChunk;
import com.agrigenius.knowledge.application.VectorKnowledgeRetriever;
import com.agrigenius.knowledge.embedding.PassagePrefixingEmbeddingModel;
import com.agrigenius.support.PostgresTestcontainer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Ingestion and retrieval against real PostgreSQL 17 + pgvector (V4 schema, Spring AI PgVectorStore),
 * with a deterministic fake embedding model and the synthetic corpus.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.ai.vectorstore.type=pgvector",
        "agrigenius.knowledge.allow-synthetic=true",
        "agrigenius.knowledge.similarity-threshold=0.3"
})
class KnowledgeIngestionIntegrationTest implements PostgresTestcontainer {

    @TestConfiguration
    static class FakeEmbeddings {
        @Bean
        EmbeddingModel embeddingModel() {
            return new PassagePrefixingEmbeddingModel(new FakeEmbeddingModel());
        }
    }

    @Autowired KnowledgeIngestionService ingestion;
    @Autowired KnowledgeRetriever retriever;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;

    @TempDir Path corpus;

    @BeforeEach
    void cleanKnowledgeAndCopyCorpus() throws IOException {
        jdbc.update("delete from knowledge_chunks");
        jdbc.update("delete from knowledge_documents");
        SyntheticCorpus.copyTo(corpus);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private UUID documentId(String fileStem) {
        return jdbc.queryForObject("select id from knowledge_documents where source_url like ?", UUID.class,
                "%/" + fileStem);
    }

    @Test
    void ingestsTheCorpusWithMetadataAndCounts() {
        IngestionReport report = ingestion.ingest(corpus);

        assertThat(report.added()).isEqualTo(3);
        assertThat(report.chunks()).isPositive();
        assertThat(count("select count(*) from knowledge_documents where status = 'ACTIVE' and data_label = 'SYNTHETIC_TEST_DATA'"))
                .isEqualTo(3);
        assertThat(count("select coalesce(sum(chunk_count), 0) from knowledge_documents")).isEqualTo(report.chunks());
        assertThat(count("select count(*) from knowledge_chunks")).isEqualTo(report.chunks());
        assertThat(jdbc.queryForObject("select crops::text from knowledge_documents where source_url like '%/paddy-leaf-spots'",
                String.class)).isEqualTo("{rice}");

        UUID paddy = documentId("paddy-leaf-spots");
        String metadata = jdbc.queryForObject(
                "select metadata::text from knowledge_chunks where metadata->>'documentId' = ? order by (metadata->>'chunkIndex')::int limit 1",
                String.class, paddy.toString());
        JsonNode m = json.readTree(metadata);
        assertThat(m.get("documentId").asText()).isEqualTo(paddy.toString());
        assertThat(m.get("chunkIndex").asInt()).isZero();
        assertThat(m.get("title").asText()).startsWith("SYNTHETIC TEST DOCUMENT");
        assertThat(m.get("dataLabel").asText()).isEqualTo("SYNTHETIC_TEST_DATA");
        assertThat(m.get("licence").asText()).isEqualTo("SYNTHETIC-TEST-ONLY");
        assertThat(m.get("language").asText()).isEqualTo("en");
        assertThat(m.get("crops").get(0).asText()).isEqualTo("rice");
        assertThat(m.get("url").asText()).endsWith("/paddy-leaf-spots");

        assertThat(jdbc.queryForList("select content from knowledge_chunks", String.class))
                .noneMatch(c -> c.startsWith("passage: "));
    }

    @Test
    void reingestingUnchangedFilesIsSkipped() {
        ingestion.ingest(corpus);
        List<String> before = jdbc.queryForList("select id::text from knowledge_chunks order by id", String.class);

        IngestionReport again = ingestion.ingest(corpus);

        assertThat(again.unchanged()).isEqualTo(3);
        assertThat(again.added() + again.replaced() + again.retired()).isZero();
        assertThat(jdbc.queryForList("select id::text from knowledge_chunks order by id", String.class)).isEqualTo(before);
    }

    @Test
    void changedFileReplacesItsChunksAndKeepsTheDocument() throws IOException {
        ingestion.ingest(corpus);
        UUID paddy = documentId("paddy-leaf-spots");
        Files.writeString(corpus.resolve("paddy-leaf-spots.md"),
                "# SYNTHETIC TEST DOCUMENT – not agricultural advice\n\n## Revised\n\nRevised synthetic paddy text about yellowing.\n");
        SyntheticCorpus.writeManifest(corpus);

        IngestionReport report = ingestion.ingest(corpus);

        assertThat(report.replaced()).isEqualTo(1);
        assertThat(report.unchanged()).isEqualTo(2);
        assertThat(documentId("paddy-leaf-spots")).isEqualTo(paddy);
        List<String> contents = jdbc.queryForList(
                "select content from knowledge_chunks where metadata->>'documentId' = ?", String.class, paddy.toString());
        assertThat(contents).isNotEmpty().allMatch(c -> !c.contains("count leaves with brown spots"))
                .anyMatch(c -> c.contains("yellowing"));
    }

    @Test
    void entryRemovedFromTheManifestIsRetiredAndItsChunksDeleted() throws IOException {
        ingestion.ingest(corpus);
        UUID soil = documentId("soil-sampling");
        String template = Files.readString(corpus.resolve("manifest.template.yaml"));
        SyntheticCorpus.writeManifest(corpus, template.substring(0, template.indexOf("  - title: \"SYNTHETIC TEST DOCUMENT – soil")));

        IngestionReport report = ingestion.ingest(corpus);

        assertThat(report.retired()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from knowledge_documents where id = ?", String.class, soil))
                .isEqualTo("RETIRED");
        assertThat(count("select count(*) from knowledge_chunks where metadata->>'documentId' = ?", soil.toString())).isZero();
        assertThat(retriever.retrieve("soil sampling depth sample bags laboratory", 5))
                .noneMatch(c -> c.documentId().equals(soil));
    }

    @Test
    void retrievalFindsTheRelevantChunkAndNothingForUnrelatedQuestions() {
        ingestion.ingest(corpus);
        assertThat(retriever).isInstanceOf(VectorKnowledgeRetriever.class);

        List<RetrievedChunk> hits = retriever.retrieve("brown spots on paddy leaves", 5);
        assertThat(hits).isNotEmpty();
        assertThat(hits.getFirst().title()).contains("paddy leaf spots");
        assertThat(hits.getFirst().similarity()).isGreaterThanOrEqualTo(0.3);
        assertThat(hits.getFirst().dataLabel()).isEqualTo("SYNTHETIC_TEST_DATA");
        assertThat(hits.getFirst().url()).endsWith("/paddy-leaf-spots");

        assertThat(retriever.retrieve("tractor tyre pressure sidewall", 5)).isEmpty();
    }

    @Test
    void invalidManifestChangesNothing() throws IOException {
        ingestion.ingest(corpus);
        int chunks = count("select count(*) from knowledge_chunks");
        Files.writeString(corpus.resolve("maize-watering.md"), "changed without updating the checksum\n");

        assertThatThrownBy(() -> ingestion.ingest(corpus)).isInstanceOf(KnowledgeManifestException.class)
                .hasMessageContaining("does not match");
        assertThat(count("select count(*) from knowledge_chunks")).isEqualTo(chunks);
        assertThat(count("select count(*) from knowledge_documents where status = 'ACTIVE'")).isEqualTo(3);
    }

    @Test
    void appDatabaseRoleHasDataOnlyAccessToKnowledgeTables() throws SQLException {
        try (Connection c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "agrigenius_app_test",
                "test-only-app-role-password")) {
            c.setAutoCommit(false);
            c.createStatement().executeQuery("select count(*) from knowledge_documents").close();
            c.createStatement().execute("insert into knowledge_chunks (id, content, metadata, embedding) values ("
                    + "gen_random_uuid(), 'x', '{}', array_fill(0.1::real, ARRAY[384])::vector)");
            c.rollback();
            for (String ddl : List.of("create table knowledge_hack (id int)", "drop table knowledge_chunks",
                    "alter table knowledge_documents add column hack int")) {
                assertThatThrownBy(() -> c.createStatement().execute(ddl))
                        .isInstanceOf(SQLException.class)
                        .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("42501"));
                c.rollback();
            }
        }
    }

    @Test
    void chunkIdsAreDeterministicPerDocumentAndIndex() {
        ingestion.ingest(corpus);
        UUID paddy = documentId("paddy-leaf-spots");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select id, (metadata->>'chunkIndex')::int as idx from knowledge_chunks where metadata->>'documentId' = ?",
                paddy.toString());
        assertThat(rows).allSatisfy(r -> assertThat(r.get("id")).isEqualTo(
                UUID.nameUUIDFromBytes((paddy + ":" + r.get("idx")).getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }
}
