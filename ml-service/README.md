# AgriGenius ML Service

Private FastAPI service for specialized ML inference. Only the Spring Boot backend calls it; it must never be exposed to the Angular client or the public internet.

Current scope: service foundation and `GET /health`. No ML models or inference endpoints yet.

## Layout

```
app/
  main.py            create_app() factory and ASGI `app`
  __main__.py        `python -m app` entry point (uses ML_HOST / ML_PORT)
  core/config.py     typed settings from ML_* environment variables
  core/logging.py    logging setup
  api/routes/        one router module per feature (health.py today)
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

Invalid values stop the service at startup with a validation error.
