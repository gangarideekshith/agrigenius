# AgriGenius Project Status

> This is the source of truth for implementation status. Update it after every completed task. Do not mark work complete without test evidence.

## Current stage
- Overall: Phase 1 – Milestone 2 in progress
- Active phase: Phase 1
- Active milestone: M2 Identity, farmer and farms (farmer registration and authentication part in progress, branch `feature/farmer-authentication`)
- Last verified update: 2026-11-17 – Spring Boot backend foundation merged (PR #2); backend and ML CI passed on `main`
- Current blocker: None. Angular project not yet created. Local machine needs the JVM socket-directory workaround to run the backend (see DEVELOPMENT.md).

## Phase status

| Phase | Status | Exit condition |
|---|---|---|
| Phase 1 – Core farmer platform | In progress | Four modules pass end-to-end tests and user approval |
| Phase 2 – Advanced intelligence and farm management | Locked | Explicit user approval after Phase 1 |
| Phase 3 – Complete agriculture ecosystem | Locked | Explicit user approval after Phase 2 |

## Milestones
- [ ] M0 Plan and repository setup
- [ ] M1 Phase 1 foundation (ML service and Spring Boot foundations done; Angular pending)
- [ ] M2 Identity, farmer and farms (registration/login/profile in progress; farms not started)
- [ ] M3 Crop recommendation
- [ ] M4 Weather and alerts
- [ ] M5 Agriculture chatbot
- [ ] Phase 1 exit review

## Module tracking
| Module | Status | Evidence / notes |
|---|---|---|
| Farmer registration/profile/farms | In progress | Registration, login, JWT and `GET /api/farmers/me` in progress; profile update and farms not started |
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
  - CI: `.github/workflows/backend-tests.yml` passed on PR #2 and on `main` after the merge.

## In progress
- **Farmer registration and authentication (M2, branch `feature/farmer-authentication`).** `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/farmers/me`; BCrypt (cost 12) password hashing; HS256 JWT access tokens via Spring Security's resource server; role-based authorization (FARMER self-registration only); farmer profile created with the account; problem+json errors with stable `code`; in-memory throttling; Flyway V2 (`users`, `farmer_profiles`); least-privilege database role (ADR-0003).
  - Acceptance criteria:
    - Register creates a FARMER user and profile in one transaction and returns `201` with the profile, never a password or hash; duplicate number/email → `409` (also under concurrent requests); invalid input → `400` with field errors; a `role` in the request has no effect.
    - Passwords are stored only as `{bcrypt}` hashes.
    - Login returns a 30-minute HS256 token with `iss`, `aud`, `sub`, `roles`, `iat`, `nbf`, `exp`, `jti` and no personal data; unknown number, wrong password and disabled account all give the same `401`.
    - `GET /api/farmers/me` returns the caller's profile for a valid FARMER token; missing/invalid/expired/tampered/`alg:none`/wrong-issuer/wrong-audience tokens → `401` with `WWW-Authenticate: Bearer`; other roles → `403`; disabled account → `401`.
    - Throttling returns `429` with `Retry-After`.
    - `JWT_SECRET` and `DB_PASSWORD` come only from the environment; startup fails if they are missing or weak.
    - The application connects as a role that cannot create, alter or drop tables.
    - Logs contain no passwords, hashes or tokens.
    - All existing tests still pass; docs updated.
  - Evidence (2026-11-17, branch `feature/farmer-authentication`, not yet committed): `./mvnw -B verify` → **173 tests, 0 failures** (unit, MockMvc slices, Testcontainers on `pgvector/pgvector:0.8.7-pg17`). Covered: register → login → `/me`; BCrypt `{bcrypt}$2` hashes; role/status mass assignment ignored; duplicate mobile/email 409 incl. 8 concurrent registrations (one 201, no 500); identical 401 for wrong password / unknown number / disabled account; tampered, foreign-key, expired, wrong-iss/aud and `alg: none` tokens 401 with `WWW-Authenticate: Bearer`; ADMIN token on `/me` 403; throttling 429 with `Retry-After`; app DB role cannot CREATE/DROP/ALTER (SQLState 42501); logs contain no password, hash, token or full mobile number.
  - Real local DB smoke test (schema backed up first): V2 applied; `agrigenius_app` created (not superuser, data-only grants); all 10 pool connections ran as `agrigenius_app`; register 201 / duplicate 409 / invalid 400 / wrong password 401 / login 200 / `/me` 200 / tampered 401; smoke-test account deleted afterwards.
  - Before running locally: add `JWT_SECRET`, `DB_USERNAME`, `DB_PASSWORD` to your `.env` (see DEVELOPMENT.md).
- Least-privilege database role (D8): implemented in this milestone through Flyway's `afterMigrate.sql` callback; the backend connects as `DB_USERNAME`, Flyway as `POSTGRES_USER`.

## Decisions (Milestone 2 authentication, approved 2026-11-17)

| # | Decision | Outcome |
|---|---|---|
| D1 | Password recovery and refresh tokens | **Deferred** to the next authentication milestone. PROJECT_SPEC §3.1 lists password recovery; this deferral is deliberate and approved. `/api/auth/refresh` and `/api/auth/forgot-password` are marked "Deferred" in the API contract. |
| D2 | Login identifier | Indian mobile number (normalised to `+91…`); email optional |
| D3 | Access-token lifetime | 30 minutes (`JWT_ACCESS_TOKEN_MINUTES=30`) until refresh tokens exist |
| D4 | Roles model | One role per user (`users.role` with a check constraint) |
| D5 | Brute-force protection | In-memory throttling: 5 failed logins per number and 20 auth requests per IP per 15 minutes → `429`; no account lockout |
| D6 | Registration response | `201` with the profile, no token |
| D7 | Error `code` field | Added to all problem+json responses |
| D8 | Least-privilege DB role | Done in this milestone (Flyway as owner, app as `agrigenius_app`) |

## Blockers and decisions needed
- Service-to-service authentication between Spring Boot and the ML service must be designed before any inference endpoint is added.
- Local `.env` must gain `JWT_SECRET`, `DB_USERNAME` and `DB_PASSWORD` before the backend starts (see DEVELOPMENT.md).
- This machine's JVM cannot create NIO sockets under `AppData\Local`; set `JDK_JAVA_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\dev\tmp` or find the blocking security setting (see DEVELOPMENT.md).
- `JAVA_HOME` on this machine points at a missing JDK 17; set it to the JDK 21 path.
- Starlette `TestClient` deprecation warning recommends `httpx2`; decide whether to switch dev dependency.

## Next recommended task
Finish and verify farmer registration and authentication. Then plan the rest of Milestone 2: profile update (`PUT /api/farmers/me`) and farm CRUD with ownership checks, followed by the deferred refresh tokens and password recovery (D1). Do not start implementation until the user approves.
