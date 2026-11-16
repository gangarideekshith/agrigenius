package com.agrigenius.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared throwaway PostgreSQL for integration tests, using the same image as
 * infra/docker-compose.yml so Flyway and pgvector are tested against what runs locally.
 */
@Testcontainers
public interface PostgresTestcontainer {

    DockerImageName IMAGE = DockerImageName.parse("pgvector/pgvector:0.8.7-pg17").asCompatibleSubstituteFor("postgres");

    @Container
    @ServiceConnection
    PostgreSQLContainer POSTGRES = new PostgreSQLContainer(IMAGE);
}
