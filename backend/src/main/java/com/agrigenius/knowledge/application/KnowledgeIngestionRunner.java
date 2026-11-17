package com.agrigenius.knowledge.application;

import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Command-line ingestion, active only with the {@code ingest} profile:
 * {@code ./mvnw spring-boot:run -Dspring-boot.run.profiles=local,ingest} with {@code EMBEDDING_PROVIDER=e5-onnx}
 * and {@code VECTOR_STORE_TYPE=pgvector}. Ingests the knowledge folder
 * ({@code agrigenius.knowledge.source-dir}, default {@code ../knowledge}) and exits with 0 on success,
 * 1 on any failure. There is deliberately no HTTP endpoint for ingestion.
 */
@Component
@Profile("ingest")
public class KnowledgeIngestionRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestionRunner.class);

    private final KnowledgeIngestionService ingestion;
    private final ConfigurableApplicationContext context;
    private final Path sourceDir;

    public KnowledgeIngestionRunner(KnowledgeIngestionService ingestion, ConfigurableApplicationContext context,
            @Value("${agrigenius.knowledge.source-dir:../knowledge}") String sourceDir) {
        this.ingestion = ingestion;
        this.context = context;
        this.sourceDir = Path.of(sourceDir);
    }

    @Override
    public void run(ApplicationArguments args) {
        int exitCode;
        try {
            ingestion.ingest(sourceDir);
            exitCode = 0;
        } catch (KnowledgeManifestException e) {
            log.error(e.getMessage());
            exitCode = 1;
        } catch (RuntimeException e) {
            log.error("Knowledge ingestion failed", e);
            exitCode = 1;
        }
        int code = exitCode;
        System.exit(SpringApplication.exit(context, () -> code));
    }
}
