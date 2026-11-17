# AgriGenius ML Service

Private FastAPI service for specialized ML inference. Only the Spring Boot backend calls it; it must never be exposed to the Angular client or the public internet.

Current scope: health/readiness and the private crop recommendation API (`/v1/crop-recommendation/*`). Every `/v1` route requires the `X-Internal-Api-Key` header (ADR-0004).

## Layout

```
app/
  main.py            create_app() factory and ASGI `app`
  __main__.py        `python -m app` entry point (uses ML_HOST / ML_PORT)
  core/config.py     typed settings from ML_* environment variables
  core/logging.py    logging setup
  api/routes/        health.py (liveness + readiness)
  core/errors.py     JSON error shape for /v1 routes ({code, message, errors})
  security/          internal API key check (constant-time)
  crop_recommendation/  schemas, checksum-verified model loader, predictor, router
tests/               pytest suite
```

## Setup (Windows PowerShell, from `ml-service/`)

Python 3.12 with the virtual environment at `ml-service/.venv` (gitignored).

```powershell
python -m venv .venv                               # only if .venv does not exist
.\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt
Copy-Item .env.example .env                        # optional local overrides
```

`requirements.txt` holds runtime pins; `requirements-dev.txt` adds test tools.

## Run

```powershell
.\.venv\Scripts\python.exe -m app
# or, with explicit host/port:
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8001
Invoke-RestMethod http://127.0.0.1:8001/health
```

## Endpoints

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/health` | none | Liveness |
| GET | `/health/ready` | none | `200 {status: ready, model_loaded: true}` or `503 {status: not_ready}` |
| POST | `/v1/crop-recommendation/predict` | `X-Internal-Api-Key` | Top-k crops with probabilities, data-derived explanations and out-of-training-range warnings |
| GET | `/v1/crop-recommendation/model` | `X-Internal-Api-Key` | Loaded model metadata (version, checksum, classes, metrics) |

Contract examples: `docs/api/contracts/crop-recommendation/`. Errors on `/v1` routes: `401 UNAUTHORIZED`, `422 INVALID_REQUEST` (field list, no input values), `503 MODEL_NOT_LOADED`.

The model artifact `<ML_MODEL_DIR>/<version>/model.joblib` is loaded only if its SHA-256 matches `metadata.json` (checked before deserialising, because joblib files can execute code). Any problem leaves the model unloaded: `/health/ready` and the `/v1` routes then answer 503.

## Test

```powershell
.\.venv\Scripts\python.exe -m pytest -v
```

## Configuration

| Variable | Default | Notes |
|---|---|---|
| `ML_APP_ENV` | `local` | `local`, `test`, `staging`, `production` |
| `ML_SERVICE_NAME` | `agrigenius-ml-service` | Reported by `/health` |
| `ML_SERVICE_VERSION` | `0.1.0` | Reported by `/health` and OpenAPI |
| `ML_HOST` | `127.0.0.1` | Loopback only by default |
| `ML_PORT` | `8001` | Must match `ML_SERVICE_BASE_URL` used by Spring Boot |
| `ML_LOG_LEVEL` | `INFO` | `CRITICAL`, `ERROR`, `WARNING`, `INFO`, `DEBUG` |
| `ML_DOCS_ENABLED` | unset | Unset → `/docs` on except in production |
| `ML_INTERNAL_API_KEY` | **required** | ≥ 32 chars; must equal the backend's `ML_SERVICE_API_KEY`; placeholder rejected |
| `ML_MODEL_DIR` | `models/crop_recommendation` | Root of versioned model artifacts |
| `ML_CROP_MODEL_VERSION` | `1.0.0` | Semantic version of the artifact to load |

Invalid values stop the service at startup with a validation error.
