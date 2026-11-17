package com.agrigenius.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One throwaway PostgreSQL for all integration tests, using the same image as
 * infra/docker-compose.yml so Flyway and pgvector are tested against what runs locally.
 *
 * <p>Started once and never stopped (Testcontainers' Ryuk removes it when the JVM exits).
 * A per-class {@code @Container} would be stopped after each test class while Spring keeps
 * the cached application context pointing at it, breaking later classes that reuse the context.
 */
public interface PostgresTestcontainer {

    DockerImageName IMAGE = DockerImageName.parse("pgvector/pgvector:0.8.7-pg17").asCompatibleSubstituteFor("postgres");

    @ServiceConnection
    PostgreSQLContainer POSTGRES = Holder.start();

    final class Holder {

        private Holder() {
        }

        static PostgreSQLContainer start() {
            PostgreSQLContainer container = new PostgreSQLContainer(IMAGE);
            container.start();
            return container;
        }
    }
}
