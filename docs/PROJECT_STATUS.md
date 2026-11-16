# AgriGenius Project Status

> This is the source of truth for implementation status. Update it after every completed task. Do not mark work complete without test evidence.

## Current stage
- Overall: Repository setup / Phase 1 foundation
- Active phase: Phase 1
- Active milestone: Milestone 0 – Plan and repository setup
- Last verified update: 2026-11-17 – ML service foundation (`GET /health`), 15 pytest tests passing
- Current blocker: None for the ML service. Spring Boot and Angular projects not yet created.

## Phase status

| Phase | Status | Exit condition |
|---|---|---|
| Phase 1 – Core farmer platform | Not started | Four modules pass end-to-end tests and user approval |
| Phase 2 – Advanced intelligence and farm management | Locked | Explicit user approval after Phase 1 |
| Phase 3 – Complete agriculture ecosystem | Locked | Explicit user approval after Phase 2 |

## Milestones
- [ ] M0 Plan and repository setup
- [ ] M1 Phase 1 foundation (ML service foundation done; Spring Boot / Angular pending)
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

## In progress
- None recorded.

## Blockers and decisions needed
- Service-to-service authentication between Spring Boot and the ML service must be designed before any inference endpoint is added.
- Starlette `TestClient` deprecation warning recommends `httpx2`; decide whether to switch dev dependency.
- Confirm exact compatible dependency versions before generating projects.

## Next recommended task
Plan the Spring Boot backend foundation (health, global errors, logging, OpenAPI, Flyway baseline) and the internal Spring Boot → ML service authentication approach. Do not start feature implementation until the user approves.
