# AgriGenius Project Status

> This is the source of truth for implementation status. Update it after every completed task. Do not mark work complete without test evidence.

## Current stage
- Overall: Phase 1 – Milestone 3 (crop recommendation) in progress
- Active phase: Phase 1
- Active milestone: M3 Crop recommendation (branch `feature/crop-recommendation`)
- Last verified update: 2026-11-17 – farmer registration and authentication merged (PR #3); backend and ML CI passed on `main` (`6dd1cea`)
- Current blocker: confirmation of the crop dataset's licence before a real model is trained (ADR-0005, Q1). Angular project not yet created. Local machine needs the JVM socket-directory workaround to run the backend (see DEVELOPMENT.md).

## Phase status

| Phase | Status | Exit condition |
|---|---|---|
| Phase 1 – Core farmer platform | In progress | Four modules pass end-to-end tests and user approval |
| Phase 2 – Advanced intelligence and farm management | Locked | Explicit user approval after Phase 1 |
| Phase 3 – Complete agriculture ecosystem | Locked | Explicit user approval after Phase 2 |

## Milestones
- [ ] M0 Plan and repository setup
- [ ] M1 Phase 1 foundation (ML service and Spring Boot foundations done; Angular pending)
- [ ] M2 Identity, farmer and farms (registration, login and profile done in PR #3; profile update and farms deferred by Q2)
- [ ] M3 Crop recommendation (in progress)
- [ ] M4 Weather and alerts
- [ ] M5 Agriculture chatbot
- [ ] Phase 1 exit review

## Module tracking
| Module | Status | Evidence / notes |
|---|---|---|
| Farmer registration/profile/farms | Partly done | Registration, login, JWT and `GET /api/farmers/me` merged (PR #3); profile update and farms not started (deferred by Q2) |
| AI crop recommendation | In progress | Backend + ML service (M3, branch `feature/crop-recommendation`); UI deferred until the Angular app exists (Q7) |
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
- **Farmer registration and authentication (M2, merged in PR #3, 2026-11-17).** `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/farmers/me`; BCrypt (cost 12) password hashing; HS256 JWT access tokens via Spring Security's resource server; role-based authorization (FARMER self-registration only); farmer profile created with the account; problem+json errors with stable `code`; in-memory throttling; Flyway V2 (`users`, `farmer_profiles`); least-privilege database role (ADR-0003).
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
  - Evidence (2026-11-17, commit `e88a003`): `./mvnw -B verify` → **173 tests, 0 failures** (unit, MockMvc slices, Testcontainers on `pgvector/pgvector:0.8.7-pg17`). Covered: register → login → `/me`; BCrypt `{bcrypt}$2` hashes; role/status mass assignment ignored; duplicate mobile/email 409 incl. 8 concurrent registrations (one 201, no 500); identical 401 for wrong password / unknown number / disabled account; tampered, foreign-key, expired, wrong-iss/aud and `alg: none` tokens 401 with `WWW-Authenticate: Bearer`; ADMIN token on `/me` 403; throttling 429 with `Retry-After`; app DB role cannot CREATE/DROP/ALTER (SQLState 42501); logs contain no password, hash, token or full mobile number.
  - Real local DB smoke test (schema backed up first): V2 applied; `agrigenius_app` created (not superuser, data-only grants); all 10 pool connections ran as `agrigenius_app`; register 201 / duplicate 409 / invalid 400 / wrong password 401 / login 200 / `/me` 200 / tampered 401; smoke-test account deleted afterwards.
  - Before running locally: add `JWT_SECRET`, `DB_USERNAME`, `DB_PASSWORD` to your `.env` (see DEVELOPMENT.md).
  - Least-privilege database role (D8): Flyway's `afterMigrate.sql` callback; the backend connects as `DB_USERNAME`, Flyway as `POSTGRES_USER`.
  - CI: backend and ML workflows passed on PR #3 and on `main` after the merge (`6dd1cea`).

## In progress
- **AI crop recommendation (M3, branch `feature/crop-recommendation`).** Spring Boot `recommendation` module (`POST /api/recommendations`, `GET /api/recommendations/history`, `GET /api/recommendations/{id}`), Flyway V3 (`crops`, `crop_recommendation_requests`, `crop_recommendation_results`), private ML endpoint `POST /v1/crop-recommendation/predict` protected by `X-Internal-Api-Key` (ADR-0004), calibrated Random Forest / XGBoost model with checksum-verified artifact (ADR-0005), shared contract fixtures in `docs/api/contracts/crop-recommendation/`.
  - Acceptance criteria:
    - Only `FARMER` tokens can create or read recommendations; the user id comes from the token; another farmer's id returns `404`.
    - Inputs are validated (ranges and enums from the API contract) → `400` with field errors; values outside the training range produce `INPUT_OUTSIDE_TRAINING_RANGE` warnings, not errors.
    - Every request is stored with its input snapshot, model name/version/SHA-256 and timestamps; results store up to 5 ranked candidates with probability, suitability score, confidence and data-derived explanation.
    - Confidence: HIGH ≥ 0.60, MEDIUM ≥ 0.30, LOW < 0.30; a LOW top candidate or out-of-range input gives `LOW_CONFIDENCE` with an expert-confirmation advisory.
    - Economics are reported as unavailable; results are labelled `DEMO_PUBLIC_DATASET`.
    - ML service down or timing out → request stored as `FAILED`, API returns `503 ML_SERVICE_UNAVAILABLE`; invalid ML response → `502 ML_SERVICE_ERROR`.
    - The ML service rejects calls without the internal API key (`401`), verifies the model's SHA-256 before loading, and reports readiness at `/health/ready`; the browser never reaches it.
    - Training is reproducible (fixed seed) and documented in the model card; tests use a synthetic fixture dataset.
    - 30 recommendation requests per user per hour (`429` beyond that).
    - Logs contain no tokens, API keys or input values; all existing tests still pass; docs updated.
  - Evidence (2026-11-17, branch `feature/crop-recommendation`, not yet committed): backend `./mvnw -B verify` → 243 tests, 0 failures (incl. 6 Testcontainers recommendation integration tests: persistence, JSONB, model traceability, LOW_CONFIDENCE, ML outage → FAILED + 503, ownership 404, history isolation, app-role data-only access); ML service `pytest` → 73 passed (API key, checksum-verified loading, predict/model/readiness, contract fixtures, training determinism, leakage control, quality floor).
  - Model 1.0.0 trained on the committed dataset (SHA-256 `808e1d84…d77c`, Apache 2.0): Random Forest, CV macro-F1 0.9937 ± 0.0056, hold-out macro-F1 0.9932 (3/440 errors). Labelled DEMO_PUBLIC_DATASET; not field-validated (see MODEL_CARDS.md).
  - Two-service smoke test on the local DB (backup taken first): V3 applied; ML service loaded the checksum-verified model; through the backend only: typical rice inputs → COMPLETED, rice 0.937 HIGH; extreme inputs → LOW_CONFIDENCE with training-range warnings and KVK escalation; invalid → 400 with 4 field errors; history and own GET 200; no token 401; ML predict without key 401; logs free of passwords, tokens, mobile numbers and feature values. Smoke-test accounts deleted.

## Decisions (Milestone 3 crop recommendation, approved 2026-11-17)

| # | Decision | Outcome |
|---|---|---|
| Q1 | Training data | Public "Crop Recommendation" dataset (2,200 rows, 22 crops), **only once its licence is confirmed**; labelled `DEMO_PUBLIC_DATASET`; tests use a synthetic fixture (ADR-0005) |
| Q2 | Farms first? | No. Recommendations store their own location; `farm_id` is nullable without a foreign key until farms exist. Farm CRUD (M2 remainder) is deferred. |
| Q3 | Inputs the model doesn't use | Soil type, moisture, season, irrigation, previous crop and location are stored but not used by the model; responses list `featuresUsedByModel` |
| Q4 | Weather inputs | Typed in by the farmer (average °C, %, seasonal mm) until the weather module (M4) fills them |
| Q5 | Economics | Not provided (`available: false`): no verified market or cost data |
| Q6 | Backend → ML authentication | Shared `X-Internal-Api-Key` (≥ 32 characters, constant-time check) plus loopback binding; mTLS later (ADR-0004) |
| Q7 | UI | Backend and ML only; Angular screens deferred to the milestone that creates the Angular app |
| Q8 | Low confidence | HIGH ≥ 0.60, MEDIUM ≥ 0.30, LOW < 0.30; LOW top candidate or out-of-range input → `LOW_CONFIDENCE` with expert advisory |

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
- ~~Dataset licence~~ Resolved 2026-11-17: Apache 2.0 (Kaggle metadata); dataset committed with NOTICE and checksum; model 1.0.0 trained (artifact generated, not committed).
- Service-to-service authentication between Spring Boot and the ML service: **in progress** (ADR-0004, shared `X-Internal-Api-Key`).
- Local `.env` files need the shared `ML_SERVICE_API_KEY` (root) / `ML_INTERNAL_API_KEY` (`ml-service/.env`) once M3 lands (see DEVELOPMENT.md).
- Deferred by approved decisions: farm CRUD and profile update (Q2), Angular UI (Q7), economics (Q5), password recovery and refresh tokens (D1).
- Local `.env` must gain `JWT_SECRET`, `DB_USERNAME` and `DB_PASSWORD` before the backend starts (see DEVELOPMENT.md).
- This machine's JVM cannot create NIO sockets under `AppData\Local`; set `JDK_JAVA_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\dev\tmp` or find the blocking security setting (see DEVELOPMENT.md).
- `JAVA_HOME` on this machine points at a missing JDK 17; set it to the JDK 21 path.
- Starlette `TestClient` deprecation warning recommends `httpx2`; decide whether to switch dev dependency.

## Next recommended task
Finish M3: add the dataset (licence confirmed Apache 2.0), train the v1.0.0 model (artifact generated, not committed), then run the full backend and ML test suites and a smoke test with both services against the local database. After M3, plan the remaining M2 work (farm CRUD and profile update) so recommendations can be linked to farms.
