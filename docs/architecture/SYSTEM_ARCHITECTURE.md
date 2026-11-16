# System Architecture

## Logical components

```mermaid
flowchart TD
    U[Farmer / Admin / Expert / Buyer] --> A[Angular Client]
    A --> B[Spring Boot Modular Backend]
    B --> S[Spring Security / JWT / RBAC]
    B --> P[(PostgreSQL)]
    B --> R[(Redis - optional)]
    B --> M[Private Python ML API]
    B --> W[Weather / Market Providers]
    B --> N[Notification Provider]
    B --> C[Spring AI Chat Orchestration]
    C --> V[(Verified Knowledge / Vector Store)]
    M --> T[Versioned ML Models]
```

## Trust boundaries
- Browser is an untrusted client. Enforce all authorization on the backend.
- Python inference endpoints are internal and must not be publicly exposed to the browser.
- LLM tools must call authorized backend services; never allow raw database access or authorization bypass.
- External provider data must be validated, timestamped and attributed.

## Python ML service (`ml-service/`)
- FastAPI application created by `create_app()` in `app/main.py`; feature routers live under `app/api/routes/`, configuration in `app/core/config.py`.
- Configuration comes only from `ML_*` environment variables (optionally `ml-service/.env`, gitignored) and is validated at startup.
- Binds to `127.0.0.1:8001` by default so it is unreachable from other machines. Changing `ML_HOST` to a non-loopback address must be paired with network isolation and service-to-service authentication.
- Currently exposes only `GET /health` (liveness). No models are loaded yet.
- OpenAPI docs are disabled when `ML_APP_ENV=production`.

## Architecture decision records
Create `docs/architecture/decisions/ADR-0001-*.md` for significant choices. Each ADR should include context, decision, alternatives, consequences and date.
