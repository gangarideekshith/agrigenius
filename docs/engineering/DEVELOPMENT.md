# Development Environment and Commands

This document must contain only commands verified against the actual repository and machine. Replace pending values after setup.

## Environment baseline

- OS: To be inspected
- Java: Java 21 LTS preferred
- Spring Boot: compatible 3.x release selected during setup
- Node/npm/Angular CLI: inspect and pin compatible versions
- Python: 3.12.0 (verified), virtual environment at `ml-service/.venv`
- PostgreSQL: inspect installed/server version
- Docker: optional; inspect availability

## Initial inspection (Windows PowerShell)

```powershell
Get-Location
git status --short --branch
git --version
node --version
npm --version
ng version
java -version
mvn -version
python --version
pip --version
docker --version
```

A missing command should be recorded as unavailable; do not silently install system-wide tools.

## Environment variables

Maintain `.env.example` with variable names and safe placeholders. Keep actual `.env` files out of Git. Document where each variable is consumed and never print secret values in diagnostic output.

### ML service (`ml-service/.env.example`)

Consumed by `ml-service/app/core/config.py`. All use the `ML_` prefix and are validated at startup.

| Variable | Default | Purpose |
|---|---|---|
| `ML_APP_ENV` | `local` | `local` / `test` / `staging` / `production` |
| `ML_SERVICE_NAME` | `agrigenius-ml-service` | Reported by `/health` |
| `ML_SERVICE_VERSION` | `0.1.0` | Reported by `/health` |
| `ML_HOST` | `127.0.0.1` | Bind address; keep loopback unless network-isolated |
| `ML_PORT` | `8001` | Must match root `ML_SERVICE_BASE_URL` |
| `ML_LOG_LEVEL` | `INFO` | Python log level |
| `ML_DOCS_ENABLED` | unset | Unset → OpenAPI docs on except in production |

Verified health check while the service runs: `Invoke-RestMethod http://127.0.0.1:8001/health`.

## Verified commands

| Component | Install/build | Run | Test | Port |
|---|---|---|---|---|
| Angular | Pending | Pending | Pending | Pending |
| Spring Boot | Pending | Pending | Pending | Pending |
| PostgreSQL | Pending | Pending | Pending | Pending |
| Python ML | `cd ml-service; .\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt` | `.\.venv\Scripts\python.exe -m app` | `.\.venv\Scripts\python.exe -m pytest -v` | 8001 (127.0.0.1 only) |

## Troubleshooting log

Record the symptom, root cause, exact fix, and verification command for recurring setup problems.

- **`requirements.txt` shows as binary in Git.** Cause: Windows PowerShell 5.1 `pip freeze > requirements.txt` writes UTF-16. Fix: write pins as UTF-8 (e.g. `pip freeze | Out-File -Encoding utf8 requirements.txt`, or edit the file directly). Verify: `git diff` shows text changes.
- **pytest warns `Using httpx with starlette.testclient is deprecated`.** Cause: Starlette 1.10 prefers `httpx2` for `TestClient`. Harmless for now; tests pass. Tracked as a follow-up.
