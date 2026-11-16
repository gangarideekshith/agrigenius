# AgriGenius Project Status

> This is the source of truth for implementation status. Update it after every completed task. Do not mark work complete without test evidence.

## Current stage
- Overall: Repository setup / Phase 1 foundation
- Active phase: Phase 1
- Active milestone: Milestone 0 – Plan and repository setup
- Last verified update: 2026-11-17 – Spring Boot backend foundation, `./mvnw verify` 36 tests passing
- Current blocker: None. Angular project not yet created. Local machine needs the JVM socket-directory workaround to run the backend (see DEVELOPMENT.md).

## Phase status

| Phase | Status | Exit condition |
|---|---|---|
| Phase 1 – Core farmer platform | Not started | Four modules pass end-to-end tests and user approval |
| Phase 2 – Advanced intelligence and farm management | Locked | Explicit user approval after Phase 1 |
| Phase 3 – Complete agriculture ecosystem | Locked | Explicit user approval after Phase 2 |

## Milestones
- [ ] M0 Plan and repository setup
- [ ] M1 Phase 1 foundation (ML service and Spring Boot foundations done; Angular pending)
- [ ] M2 Identity, farmer and farms
- [ ] M3 Crop recommendation
- [ ] M4 Weather and alerts
- [ ] M5 Agriculture chatbot
- [ ] Phase 1 exit review

## Module tracking
| Module | Status | Evidence / notes |
|---|---|---|
| Farmer registration/profile/farms | Not started | |
| AI crop recommendation | Not started | |
| AI agriculture chatbot | Not started | |
| Weather forecast and smart alerts | Not started | |

## Recent completed work
- Initial project specification and roadmap scaffolded.
- Local infrastructure: PostgreSQL 17 + pgvector 0.8.7 via `infra/docker-compose.yml`; Python 3.12 venv at `ml-service/.venv`.
  - Hardening (2026-11-17): Postgres port bound to `127.0.0.1` only; required `POSTGRES_*` variables fail fast; `POSTGRES_*` documented in `.env.example`. Evidence: `docker compose ... config` shows `host_ip: 127.0.0.1`; config valid with real `.env`.
- **ML service foundation (2026-11-17).** Modular FastAPI app in `ml-service/app` with `create_app()` factory, `ML_*` environment settings (pydantic-settings, validated at startup), logging setup, loopback-only default bind, and `GET /health` liveness endpoint. OpenAPI docs off in production.
  - Acceptance criteria: `/health` returns 200 with service/version/environment; config overridable by env vars and `.env`; invalid config fails fast; venv and `.env` excluded from Git; no ML features.
  - Evidence: `python -m pytest -v` → 15 passed; live `python -m app` + `Invoke-RestMethod http://127.0.0.1:8001/health` → `status: ok`.
  - Dependencies added: `pydantic-settings` (runtime); `pytest`, `httpx` (dev, `requirements-dev.txt`). `requirements.txt` converted from UTF-16 to UTF-8.
  - CI: `.github/workflows/ml-service-tests.yml` (PR #1, merged); first GitHub run passed.
- **Spring Boot backend foundation (2026-11-17, branch `feature/spring-boot-foundation`).** Spring Boot 4.1.2 / Java 21 in `backend/` with Maven Wrapper 3.9.16 (ADR-0002: 3.x no longer receives open-source fixes). Datasource from the root `.env` (`POSTGRES_*`, overridable by `DB_*`); Flyway V1 (`CREATE EXTENSION IF NOT EXISTS vector`); stateless deny-by-default security with JSON 401/403 and a CORS allowlist; problem+json error handling with request IDs; health/liveness/readiness/info; OpenAPI in the `local` profile only; `MlServiceClient` with timeouts and a `DEGRADED` health component.
  - Acceptance criteria: app starts against the local DB; Flyway applies V1 without touching existing data; health public without details; `/api/**` returns 401; other actuator endpoints hidden; docs only in `local`; ML outage does not take the backend down; invalid configuration fails at startup; no secrets in source.
  - Evidence: `./mvnw -B verify` → 36 tests, 0 failures (unit, MockMvc slice, Testcontainers on `pgvector/pgvector:0.8.7-pg17`). Real DB: schema backed up first; `flyway_schema_history` shows V1 success, no other tables; smoke tests: health `UP`, `/api/anything` 401 problem+json, `/actuator/env` 401, api-docs 200 (local), unknown CORS origin 403. With the real ML service running `mlService` = UP; after stopping it `mlService` = DEGRADED and overall health stays `UP`.
  - CI: `.github/workflows/backend-tests.yml` (not yet run on GitHub).

## In progress
- Spring Boot foundation awaiting review/commit on `feature/spring-boot-foundation`.

## Blockers and decisions needed
- Service-to-service authentication between Spring Boot and the ML service must be designed before any inference endpoint is added.
- The backend connects as the database superuser created by the Postgres image. Add a least-privilege application role before Milestone 2 (database change; needs approval).
- This machine's JVM cannot create NIO sockets under `AppData\Local`; set `JDK_JAVA_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\dev\tmp` or find the blocking security setting (see DEVELOPMENT.md).
- `JAVA_HOME` on this machine points at a missing JDK 17; set it to the JDK 21 path.
- Starlette `TestClient` deprecation warning recommends `httpx2`; decide whether to switch dev dependency.

## Next recommended task
Milestone 2 planning: finalize the users/roles/farmer/farm part of the ERD, the JWT token lifecycle and the least-privilege database role, then plan registration/login and farm CRUD with ownership checks. Do not start implementation until the user approves.
