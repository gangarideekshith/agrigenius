# AgriGenius – Stage 1 Handoff and Future Roadmap (Stages 2 and 3)

> Purpose: let a new development session continue Stages 2 and 3 without repeating the project analysis.
> Written 2026-11-17 on branch `feature/stage1-ui` (based on `main` @ `b6c1713`, "Merge pull request #5").
> **Completed** = implemented, tested and verified. **Planned** = design only, nothing implemented.
> Source of truth for status remains `docs/PROJECT_STATUS.md`; product scope is `docs/PROJECT_SPEC.md`.

---

## 1. Project overview and architecture

AgriGenius is an AI-assisted agriculture platform for Indian farmers (languages: English, Hindi, Telugu, Kannada, Tamil).

```
Browser ── Angular 22 SPA (frontend/, dev :4200, proxies /api)
              │ HTTPS JSON, Bearer JWT
              ▼
        Spring Boot 4.1.2 modular monolith (backend/, :8080)       Java 21, Maven Wrapper
        ├─ identity · farmer · recommendation · chat · knowledge · integration/ml
        ├─ Spring Security (stateless, deny-by-default, FARMER role)
        ├─ JPA/Hibernate (validate) + Flyway ──► PostgreSQL 17 + pgvector 0.8.7 (Docker, 127.0.0.1:5432)
        ├─ RestClient + X-Internal-Api-Key ──► FastAPI ML service (ml-service/, 127.0.0.1:8001, private)
        └─ Spring AI 2.0.2 ──► Anthropic Claude (optional; off without a key)
                         └─► in-process multilingual-e5-small ONNX embeddings (optional)
```

Key decisions (all in `docs/architecture/decisions/`): ADR-0001 modular monolith · ADR-0002 Spring Boot 4.1 · ADR-0003 JWT auth · ADR-0004 internal service auth · ADR-0005 crop model and dataset · ADR-0006 LLM provider and RAG · ADR-0007 prompt-injection and tool policy · ADR-0008 Angular frontend.

Trust boundaries: the browser only talks to Spring Boot; the ML service and the LLM provider are reachable only from the backend; secrets live only in environment variables.

---

## 2. Completed Stage 1 features (verified)

| Feature | Status | Evidence |
|---|---|---|
| Farmer registration, login, JWT (HS256, 30 min), BCrypt, throttling | **Completed** (PR #3) | Backend integration + security tests; browser journey 2026-11-17 |
| Farmer profile view **and edit** (`GET/PUT /api/farmers/me`) | **Completed** (this branch adds PUT + UI) | `FarmerProfileUpdateIntegrationTest` (5), profile UI specs, browser journey |
| AI crop recommendation (RF model 1.0.0, demo dataset) | **Completed** (PR #4) | Backend + ML tests; browser journey (rice 93.7 % HIGH) |
| AI agriculture chatbot (Spring AI, RAG, tools) | **Completed but disabled until an API key is configured** (PR #5) | Scripted-model integration tests; browser journey shows CHAT_DISABLED cleanly |
| Knowledge ingestion + retrieval (pgvector, e5 embeddings) | **Completed, no real documents yet** (PR #5) | Testcontainers ingestion/retrieval tests; real model check |
| Angular UI: auth, dashboard, profile, crop advice, history, chat | **Completed** (PR #5 + this branch) | 56 frontend tests, production build, browser journey |

Not part of Stage 1 delivery but listed in the spec's Phase 1 (see §10): farms/plots CRUD, password recovery, refresh tokens, weather.

---

## 3. Frontend (Angular 22.2, `frontend/`)

| Path | Purpose |
|---|---|
| `src/app/app.routes.ts` | Lazy routes: `/login`, `/register`, `/dashboard`, `/profile`, `/recommendations`, `/recommendations/new`, `/recommendations/:id`, `/chat` (all but auth guarded by `authGuard`) |
| `src/app/shell.ts` | Header, responsive navigation (menu button < 768 px), language switcher, sign-out |
| `src/app/core/auth/` | `AuthService` (token in memory + `sessionStorage`, auto-logout at expiry), `authInterceptor` (Bearer on `/api`, not on `/api/auth/*`), `errorInterceptor` (401 → sign-out), guards |
| `src/app/core/api/` | `api.services.ts` (FarmerApi incl. `updateMe`, RecommendationApi, ChatApi), `models.ts`, problem+json parsing and error-code → message mapping |
| `src/app/core/i18n/` + `src/assets/i18n/*.json` | Signal-based translation; English complete; hi/te/kn/ta are **DRAFT** (fall back to English) |
| `src/app/core/validation/` | Client validators mirroring backend rules |
| `src/app/features/auth/` | Login, registration |
| `src/app/features/dashboard/` | Greeting, profile summary, latest crop advice (real history API), shortcuts |
| `src/app/features/profile/` | View + edit profile (mobile read-only), save via PUT, success/validation/409 feedback |
| `src/app/features/recommendations/` | Form (units, ranges, help), result (ranked candidates, confidence, comparisons, warnings, advisory, demo label), history |
| `src/app/features/chat/` | Sessions list, conversation, composer (2000 chars), sources, NO_SOURCES/REFUSED/FAILED states, delete with confirmation, "not configured" banner |
| `src/app/shared/` | Field errors, loading, error banner |
| `proxy.conf.json` | `/api` → `http://localhost:8080` |

Design: dark green / leaf green / white, plain SCSS (`src/styles.scss`), mobile-first, accessible (labels, aria-live, focus, reduced motion). No third-party UI libraries. Note: Angular 22 defaults to OnPush — components that read form state must subscribe to form events (see `shared/field-error.ts`).

---

## 4. Backend modules and API endpoints

Package root `com.agrigenius`; each module has `api` (controllers, DTOs) → `application` (services) → `domain` (entities) → `infrastructure` (repositories). Modules talk through `application` services only.

| Module | Endpoints (all under `/api`) | Auth |
|---|---|---|
| identity | `POST /auth/login` | public (throttled) |
| farmer | `POST /auth/register`; `GET /farmers/me`; `PUT /farmers/me` | register public; others FARMER |
| recommendation | `POST /recommendations`; `GET /recommendations/history?page&size`; `GET /recommendations/{id}` | FARMER, owner-scoped (foreign id → 404) |
| chat | `POST/GET /chat/sessions`; `GET/DELETE /chat/sessions/{id}`; `GET/POST /chat/sessions/{id}/messages` | FARMER, owner-scoped |
| knowledge | no HTTP API; ingestion via `ingest` profile | — |
| operations | `/actuator/health`, `/health/liveness`, `/health/readiness`, `/info`; `/v3/api-docs`, `/swagger-ui.html` (local profile only) | public |

Errors are problem+json with a stable `code` (full list in `docs/api/API_CONTRACT.md`). Internal ML contract and fixtures: `docs/api/contracts/crop-recommendation/`.

---

## 5. Database (PostgreSQL 17 + pgvector, Flyway)

| Migration | Contents |
|---|---|
| V1 | `CREATE EXTENSION vector` |
| V2 | `users` (UUID, mobile unique, lowercase email unique, role, status), `farmer_profiles` (1:1) |
| V3 | `crops` (22 seeded), `crop_recommendation_requests`, `crop_recommendation_results` |
| V4 | `chat_sessions`, `chat_messages`, `chat_usage_daily`, `knowledge_documents`, `knowledge_chunks` (vector(384), HNSW) |
| `afterMigrate.sql` | Creates/updates the least-privilege app role (`DB_USERNAME`) with data-only grants after every migrate |

Flyway runs as the owner (`POSTGRES_USER`); the app connects as `DB_USERNAME`. `clean` is disabled; Hibernate only validates. Next migration number: **V5**. Details: `docs/database/ERD.md`.

---

## 6. Authentication and authorization

- Mobile number + password; BCrypt cost 12 (`{bcrypt}` prefix); HS256 access token (30 min) with `iss`, `aud`, `sub` (user UUID), `roles`, no personal data.
- Roles: FARMER (self-registration), ADMIN, AGRICULTURAL_EXPERT, BUYER, VENDOR (exist in the enum/DB; no creation flow yet).
- URL rules in `config/SecurityConfig.java`; ownership enforced in services (user id only from the token).
- Throttling: 5 failed logins per number, 20 auth requests per IP per 15 min; recommendations 30/user/hour; chat 20 per 10 min and 40/day per farmer + global token budget.
- Deferred: refresh tokens, password recovery (decision D1).

---

## 7. ML service integration

- FastAPI service, private (127.0.0.1:8001), `X-Internal-Api-Key` (backend `ML_SERVICE_API_KEY` must equal `ML_INTERNAL_API_KEY`).
- Endpoints: `GET /health`, `GET /health/ready`, `POST /v1/crop-recommendation/predict`, `GET /v1/crop-recommendation/model`.
- Model artifact is generated locally/CI from the committed dataset (`ml-service/data/raw/Crop_recommendation.csv`, Apache-2.0, SHA-256 in `NOTICE.md`) and is never committed. Model card: `docs/ml/MODEL_CARDS.md` (demo data, not field-validated).

---

## 8. Running locally (Windows; see `docs/engineering/DEVELOPMENT.md`)

Prerequisites: JDK 21 (`JAVA_HOME` set to it), Docker Desktop, Python 3.12 venv in `ml-service/.venv`, Node 24 + npm 11.
On this machine also set `JDK_JAVA_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\dev\tmp` (JVM NIO issue under `AppData\Local`).

```bash
docker compose --env-file .env -f infra/docker-compose.yml up -d          # PostgreSQL + pgvector
cd ml-service && .venv/Scripts/python.exe -m training.train_crop_model --csv data/raw/Crop_recommendation.csv --out models/crop_recommendation --version 1.0.0 --data-label DEMO_PUBLIC_DATASET --seed 42   # once
cd ml-service && .venv/Scripts/python.exe -m app                             # ML service :8001
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local        # backend :8080
cd frontend && npm ci && npx ng serve                                       # UI :4200 (proxy to :8080)
```

### Environment variable names (values never committed)

Root `.env`: `SPRING_PROFILES_ACTIVE` (not read from .env; pass on the command line), `SERVER_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT`, `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `DB_MANAGE_APP_ROLE`, `JWT_ISSUER`, `JWT_SECRET`, `JWT_ACCESS_TOKEN_MINUTES`, `JWT_REFRESH_TOKEN_DAYS` (unused), `CORS_ALLOWED_ORIGINS`, `ML_SERVICE_BASE_URL`, `ML_SERVICE_CONNECT_TIMEOUT`, `ML_SERVICE_READ_TIMEOUT`, `ML_SERVICE_API_KEY`, `CHAT_PROVIDER`, `ANTHROPIC_API_KEY`, `CHAT_MODEL`, `CHAT_EFFORT`, `CHAT_MAX_OUTPUT_TOKENS`, `CHAT_MESSAGES_PER_10_MIN`, `CHAT_DAILY_MESSAGE_LIMIT`, `CHAT_DAILY_TOKEN_BUDGET`, `CHAT_SIMILARITY_THRESHOLD`, `EMBEDDING_PROVIDER` (`e5-onnx`|`none`), `VECTOR_STORE_TYPE` (`pgvector`|`none`), `EMBEDDING_MODEL_CACHE_DIR`. Placeholders reserved for later: `WEATHER_API_KEY`, `LLM_PROVIDER`, `LLM_API_KEY`, `FIREBASE_CREDENTIALS_PATH`.
`ml-service/.env`: `ML_APP_ENV`, `ML_SERVICE_NAME`, `ML_SERVICE_VERSION`, `ML_HOST`, `ML_PORT`, `ML_LOG_LEVEL`, `ML_INTERNAL_API_KEY`, `ML_MODEL_DIR`, `ML_CROP_MODEL_VERSION`.

### Tests and builds

| Component | Command | Last result (2026-11-17) |
|---|---|---|
| Backend | `cd backend && ./mvnw -B verify` (Docker required) | 365 tests, 0 failures (see PROJECT_STATUS for latest) |
| ML service | `cd ml-service && .venv/Scripts/python.exe -m pytest -q` | 73 passed |
| Frontend | `cd frontend && npx ng test --watch=false` and `npx ng build` | 56 passed; build OK |
| Live LLM eval (costs money, opt-in) | `LIVE_LLM_EVAL=true ./mvnw -B test -Dtest=LiveChatEvaluationTest -Dsurefire.excludedGroups=` | not yet run (no key) |

CI: `.github/workflows/backend-tests.yml`, `ml-service-tests.yml`, `frontend-tests.yml` (PRs to `main` and pushes to `main`).

---

## 9. Git state

- Latest merged commit on `main`: `b6c1713` — Merge pull request #5 (chatbot, RAG, Angular UI).
- Stage 1 completion work: branch `feature/stage1-ui` (profile update API + UI, dashboard overview, chat/result UX fixes, this document). Not committed until the owner approves.

---

## 10. Known limitations and unfinished Stage 1 work

1. **Chat needs an API key**: set `CHAT_PROVIDER=anthropic` + `ANTHROPIC_API_KEY`; run the live spike and live eval (~$1 approved). Until then chat answers `CHAT_DISABLED`.
2. **No real knowledge documents**: choose sources (decision C3; candidates with licence status in `docs/knowledge/CANDIDATE_SOURCES.md`); only Wikipedia/Wikibooks (CC BY-SA 4.0) are confirmed so far. Tune `CHAT_SIMILARITY_THRESHOLD` (0.75 is too permissive for e5).
3. **Crop model is demo-only** (public dataset, unclear units/provenance); not field-validated.
4. **Spec Phase 1 items not yet built**: farms/plots CRUD (spec 3.1), password recovery and refresh tokens (D1, deferred).
5. **Translations**: hi/te/kn/ta UI strings and chatbot fallback texts are drafts needing native-speaker review.
6. **Not yet done**: browser end-to-end test suite (Playwright/Cypress), automated accessibility audit, load testing.
7. **Single-instance assumptions**: in-memory throttles; move to Redis if scaled out.
8. **Local machine quirks**: `JAVA_HOME` points to a missing JDK 17; JVM NIO socket issue (workaround above).

---

## 11. Stage 2 – planned (document only, NOT implemented)

General rules for every feature: new Flyway migration (V5+), new module package with api/application/domain/infrastructure, FARMER ownership checks, problem+json codes, Angular lazy route + loading/empty/error states, unit + slice + Testcontainers tests, docs (API contract, ERD, ADR when a significant choice is made), and update `PROJECT_STATUS.md`.

### 2.1 Weather upgrade (forecasts, alerts, farmer-specific)
- **Prerequisite**: farms with coordinates (Stage 1 remainder, spec 3.1) — build `farms` + `farm_locations` first.
- **Frontend**: farm weather card on dashboard; `/weather/:farmId` (hourly/daily, rainfall, wind, source + retrieval time + forecast period); alerts inbox with read status; alert threshold settings.
- **Backend**: `weather` module; provider adapter interface (Open-Meteo first; IMD if access/licence allows); scheduled refresh; alert rules engine with deduplication; chatbot tool `getMyFarmWeather(farmId)` (owner-checked).
- **API**: `GET /api/farms/{id}/weather`, `GET /api/weather/alerts?page`, `PATCH /api/weather/alerts/{id}` (read), `PUT /api/farms/{id}/alert-settings`.
- **Database**: `weather_records` (farm, source, retrieved_at, period, payload jsonb), `weather_alerts` (farm, type, severity, window, dedup key unique, read_at), `alert_settings`.
- **Dependencies**: none beyond RestClient; Firebase only when push is required (credentials via env); optional Redis cache.
- **Tests**: adapter contract tests with recorded fixtures; scheduler idempotency; dedup; ownership; UI shows source/time; never fabricate (stale data labelled).

### 2.2 Crop disease detection
- **Frontend**: `/disease/new` (camera/upload, crop select, preview), result with confidence, symptoms/causes/prevention, "send to expert" for low confidence; history.
- **Backend**: `disease` module; secure upload (type/size/magic-byte checks, re-encode, EXIF strip), object storage adapter (local disk dev, cloud later); orchestration to ML.
- **ML**: new FastAPI endpoint `/v1/disease/predict` (CNN e.g. MobileNetV3/EfficientNet), licensed dataset (e.g. PlantVillage – verify licence), model card, calibration and abstention threshold.
- **API**: `POST /api/disease/reports` (multipart), `GET /api/disease/reports[/{id}]`.
- **Database**: `disease_reports` (user, farm?, crop, image key, sha256, status, model version, top predictions jsonb, confidence, escalated_at).
- **Dependencies**: Python `onnxruntime` or `torch` (decide in ADR), image libs (Pillow), storage SDK when cloud.
- **Tests**: upload validation (oversize, wrong type, polyglot files), ML contract fixtures, low-confidence escalation, ownership, no pesticide prescription.

### 2.3 Soil analysis and fertilizer recommendations
- **Frontend**: soil test report entry (units shown, ranges), interpretation view, history per farm.
- **Backend**: `soil` module; interpretation rules from cited agronomy sources (no invented doses); fertilizer guidance only where an approved source supports it; expert review flag.
- **API**: `POST/GET /api/farms/{id}/soil-reports`, `GET /api/soil-reports/{id}/interpretation`.
- **Database**: `soil_test_reports` (farm, sampled_at, lab, N/P/K/pH/EC/OC/micronutrients with units), `soil_interpretations`.
- **Tests**: unit-conversion tests, range validation, rule tests with source citations; chatbot tool `getMySoilReports`.

### 2.4 Smart irrigation
- **Frontend**: irrigation schedule per farm/crop cycle, log watering, weather-aware suggestions.
- **Backend**: `irrigation` module combining crop stage (2.5), soil moisture (manual or sensor), water availability and forecast (2.1).
- **API**: `GET /api/crop-cycles/{id}/irrigation-plan`, `POST /api/farms/{id}/irrigation-records`.
- **Database**: `irrigation_records`, optional `sensor_readings` (MQTT/ESP32 only if justified).
- **Dependencies**: optional MQTT broker (ADR first).
- **Tests**: plan calculation tests with fixtures; missing-data behaviour (no guessing).

### 2.5 Crop lifecycle management
- **Frontend**: start a crop cycle (from a recommendation or manually), stage timeline, tasks list (daily/weekly), reminders.
- **Backend**: `lifecycle` module; stage templates per crop (sourced), task generation, reminders scheduler.
- **API**: `POST/GET /api/crop-cycles`, `GET/PATCH /api/crop-cycles/{id}/tasks/{taskId}`.
- **Database**: `crop_cycles` (farm, crop, sown_at, status), `farm_tasks` (cycle, due, type, done_at), `crop_stage_templates`.
- **Tests**: task generation, timezone handling, ownership, notifications idempotency.

### 2.6 Market price information
- **Frontend**: mandi price search (commodity, market, date range) with source and date; simple trend chart; clearly labelled forecasts.
- **Backend**: `market` module; provider adapter (Agmarknet / data.gov.in – verify licence and rate limits); scheduled ingestion; forecasts only with documented models.
- **API**: `GET /api/market/prices?commodity&state&district&from&to`, `GET /api/market/mandis`.
- **Database**: `mandis`, `market_prices` (mandi, commodity, date, min/max/modal, source, retrieved_at; unique key).
- **Tests**: ingestion idempotency, unit normalisation, never show undated prices; chatbot tool `getMarketPrices` with source attribution.

### 2.7 Farm expense tracking
- **Frontend**: expenses per crop cycle (categories), harvest records, revenue and profit summary, CSV export.
- **Backend**: `finance` module; money as `NUMERIC(14,2)` + currency INR.
- **API**: `POST/GET /api/crop-cycles/{id}/expenses`, `POST/GET /api/crop-cycles/{id}/harvests`, `GET /api/crop-cycles/{id}/summary`.
- **Database**: `expenses`, `harvest_records`, `expense_categories`.
- **Tests**: rounding/aggregation, ownership, export escaping.

Recommended Stage 2 order: farms (Stage 1 remainder) → 2.1 weather → 2.5 lifecycle → 2.7 expenses → 2.3 soil → 2.4 irrigation → 2.6 market → 2.2 disease (largest ML effort).

---

## 12. Stage 3 – planned (document only, NOT implemented)

Cross-cutting prerequisites: admin-managed account creation for non-farmer roles, refresh tokens, audit logging (`audit_logs`), per-role navigation in Angular (role-aware guards), and resource-level authorization tests for every role pair.

| Feature | Architecture / backend | UI | Database | Permissions | Testing |
|---|---|---|---|---|---|
| **3.1 Government schemes** | `schemes` module; curated catalogue from official sources (source URL + last-verified date); eligibility shown as *information*, never as a decision | Scheme list/search, details with official link and deadline | `government_schemes`, `scheme_criteria`, `scheme_sources` | Read: FARMER; write: ADMIN/EXPERT | Stale-source warnings, no fabricated eligibility, admin CRUD auth |
| **3.2 Farmer marketplace** | `marketplace` module; listings, offers, order lifecycle state machine | Create/manage listings, browse, order status | `marketplace_listings`, `orders`, `order_events` | Seller = owning FARMER/VENDOR; buyer = BUYER | State-machine tests, ownership, concurrency (stock), no payments in scope until an ADR |
| **3.3 Buyer module** | `buyer` profile + search + orders (uses 3.2) | Buyer registration (admin-approved or verified), dashboard | `buyers` (1:1 users) | BUYER only on own orders | Role isolation, data minimisation (farmer contact shared only after order) |
| **3.4 Vendor module** | `vendor` profile, product catalogue (inputs, equipment) | Vendor dashboard, catalogue management | `vendors`, `vendor_products` | VENDOR on own catalogue | Catalogue validation, ownership |
| **3.5 Admin dashboard** | `admin` module; user/role management, content moderation, scheme/crop/knowledge management, analytics | `/admin/**` area, tables with pagination/filters | uses existing tables + `audit_logs` | ADMIN only; every action audited | Authorization matrix tests, audit coverage, no PII in logs |
| **3.6 Agricultural expert dashboard** | `expert` module; review queue for low-confidence disease reports and flagged chatbot answers; verify knowledge documents | Review queue, case detail, respond/verify | `expert_reviews`, `knowledge_document_reviews` | AGRICULTURAL_EXPERT; sees only cases routed to them | Queue assignment, SLA timestamps, farmer receives expert response |

Then final integration: security hardening (rate limits in Redis, HTTPS, CSP), performance, backup/restore, containerisation and deployment.

---

## 13. Recommended order from here

1. Owner actions: add `ANTHROPIC_API_KEY` and run the live spike/eval; choose knowledge sources (C3) and ingest them; tune the retrieval threshold.
2. Finish Stage 1 remainder: farms/plots CRUD (needed by weather), then password recovery + refresh tokens (D1).
3. Browser E2E suite for the Stage 1 journey (register → profile → advice → chat).
4. Stage 2 in the order of §11, each as its own branch/PR with plan → approval → build → verify.
5. Stage 3 after Stage 2 sign-off.
