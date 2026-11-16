# AgriGenius Backend

Spring Boot modular monolith: the primary backend for AgriGenius. Angular calls this service; this service calls PostgreSQL and the private Python ML service. The browser never calls the ML service.

Current scope: **foundation only**: configuration, database connection and Flyway, security baseline, error handling, health and API docs, and the ML service client. No business features yet.

## Stack

Java 21 · Spring Boot 4.1.2 (Spring Framework 7, Spring Security 7) · Hibernate 7 · Flyway 12 · PostgreSQL 17 + pgvector · springdoc-openapi 3 · JUnit 6, Mockito, Testcontainers 2 · Maven 3.9.16 via the Maven Wrapper. See ADR-0002 for why 4.1 rather than 3.x.

## Layout

```
src/main/java/com/agrigenius/
  AgriGeniusApplication.java
  common/error/      GlobalExceptionHandler: problem+json errors with request IDs
  common/security/   JSON 401/403 responses
  common/web/        RequestIdFilter (X-Request-Id)
  config/            SecurityConfig, CorsProperties, OpenApiConfig
  integration/ml/    MlServiceClient port, RestClient adapter, health indicator
src/main/resources/
  application.yml, application-local.yml, application-prod.yml
  db/migration/      Flyway migrations (V1 enables pgvector)
```

Feature modules (`farm`, `identity`, …) will be added as packages with `api/` (controllers, DTOs), `application/` (services), `domain/` (entities) and `infrastructure/` (repositories, adapters).

## Prerequisites

- JDK 21 with `JAVA_HOME` pointing at it (for example `C:\Program Files\Java\jdk-21.0.12.1`).
- Docker running, for the local database (`infra/docker-compose.yml`) and for integration tests (Testcontainers).
- The root `.env` (copied from `.env.example`). The backend imports it automatically.

## Commands (from `backend/`)

```bash
./mvnw -B verify                                         # build + all tests (Docker required)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local  # run against the local database
```

Windows PowerShell: use `.\mvnw.cmd` instead of `./mvnw`.

The `local` profile enables Swagger UI. With no profile, API docs are off; deployments use `prod`.

## Endpoints

| Path | Access | Notes |
|---|---|---|
| `/actuator/health` | Public | `UP`/`DOWN`, no details |
| `/actuator/health/liveness`, `/actuator/health/readiness` | Public | Readiness = database + app state |
| `/actuator/info` | Public | App name and description |
| `/v3/api-docs`, `/swagger-ui.html` | Public in `local` only | 404/401 otherwise |
| `/api/**` | Authenticated | 401 problem+json until JWT exists (Milestone 2) |

## Configuration

All settings come from environment variables or the root `.env`; see `.env.example` and `docs/engineering/DEVELOPMENT.md`. Invalid settings stop startup (for example a `*` CORS origin or a missing ML service URL).

## Known machine issue (Windows)

On this development machine the JVM cannot create NIO selectors while its Unix-domain socket directory is under `AppData\Local` (`Unable to establish loopback connection`), which stops Tomcat from starting. See the troubleshooting entry in `docs/engineering/DEVELOPMENT.md` for the workaround.
