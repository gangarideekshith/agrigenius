# Development Environment and Commands

This document must contain only commands verified against the actual repository and machine. Replace pending values after setup.

## Environment baseline

- OS: Windows 11 (verified)
- Java: JDK 21.0.12.1 at `C:\Program Files\Java\jdk-21.0.12.1` (verified). `JAVA_HOME` must point at it; the Maven Wrapper refuses to start otherwise.
- Maven: 3.9.16 via the Maven Wrapper in `backend/` (no system Maven needed; download verified by SHA-256)
- Spring Boot: 4.1.2 (see ADR-0002)
- Node/npm/Angular CLI: inspect and pin compatible versions
- Python: 3.12.0 (verified), virtual environment at `ml-service/.venv`
- PostgreSQL: 17.12 + pgvector 0.8.7 in Docker (verified)
- Docker: Docker Desktop 29.9.3, Compose v5.7.0 (verified). Required for the local database and backend integration tests.

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

### Local PostgreSQL (root `.env.example`)

Consumed by `infra/docker-compose.yml` (PostgreSQL 17 + pgvector 0.8.7). `POSTGRES_DB`, `POSTGRES_USER` and `POSTGRES_PASSWORD` are required; compose stops with a clear error if one is missing. The Spring Boot `DB_*` values must match them (`DB_NAME` = `POSTGRES_DB`, `DB_USERNAME` = `POSTGRES_USER`, `DB_PASSWORD` = `POSTGRES_PASSWORD`, `DB_PORT` = `POSTGRES_PORT`).

| Variable | Example | Purpose |
|---|---|---|
| `POSTGRES_DB` | `agrigenius` | Database created on first start of an empty volume |
| `POSTGRES_USER` | `agrigenius_app` | Database owner created on first start |
| `POSTGRES_PASSWORD` | placeholder | Local password only; never commit a real value |
| `POSTGRES_PORT` | `5432` | Host port, bound to `127.0.0.1` only |

Validate the compose file without printing secrets (from the repo root):

```powershell
docker compose --env-file .env -f infra/docker-compose.yml config --quiet
```

The `POSTGRES_*` values only take effect when the data volume is empty. Changing them later does not change the existing database user or password.

### Spring Boot backend (root `.env.example`)

Consumed by `backend/src/main/resources/application.yml`, which imports the root `.env` as a properties file (`optional:file:.env`, `optional:file:../.env`). Real environment variables override `.env`. Settings are validated at startup.

| Variable | Default | Purpose |
|---|---|---|
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_PORT` | required (port 5432) | Database connection, shared with Docker Compose |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | fall back to `localhost` / `POSTGRES_*` | Optional overrides for a different database or user |
| `SERVER_PORT` | `8080` | HTTP port |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | Comma-separated browser origins for `/api/**`; `*` is rejected |
| `ML_SERVICE_BASE_URL` | `http://localhost:8001` | Private ML service; never sent to the browser |
| `ML_SERVICE_CONNECT_TIMEOUT`, `ML_SERVICE_READ_TIMEOUT` | `2s`, `5s` | ML service HTTP timeouts |
| `SPRING_PROFILES_ACTIVE` | none | **Not read from `.env`** (verified). Set it as a real environment variable or with `-Dspring-boot.run.profiles=local`. `local` enables Swagger UI; `prod` uses JSON logs. |
| `JWT_*` | — | Not used yet (Milestone 2) |

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
| Spring Boot | `cd backend; ./mvnw -B verify` (Docker must be running) | `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` (see the JVM socket troubleshooting entry on this machine) | `./mvnw -B verify` → 36 tests; `curl http://localhost:8080/actuator/health` → `{"status":"UP"}` | 8080 |
| PostgreSQL | n/a (image `pgvector/pgvector:0.8.7-pg17`) | `docker compose --env-file .env -f infra/docker-compose.yml up -d` (repo root) | `docker compose --env-file .env -f infra/docker-compose.yml config --quiet`; `docker ps` shows `(healthy)` | 5432 (127.0.0.1 only) |
| Python ML | `cd ml-service; .\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt` | `.\.venv\Scripts\python.exe -m app` | `.\.venv\Scripts\python.exe -m pytest -v` | 8001 (127.0.0.1 only) |

## Troubleshooting log

Record the symptom, root cause, exact fix, and verification command for recurring setup problems.

- **`requirements.txt` shows as binary in Git.** Cause: Windows PowerShell 5.1 `pip freeze > requirements.txt` writes UTF-16. Fix: write pins as UTF-8 (e.g. `pip freeze | Out-File -Encoding utf8 requirements.txt`, or edit the file directly). Verify: `git diff` shows text changes.
- **pytest warns `Using httpx with starlette.testclient is deprecated`.** Cause: Starlette 1.10 prefers `httpx2` for `TestClient`. Harmless for now; tests pass. Tracked as a follow-up.
- **`ports are not available ... bind: Only one usage of each socket address` when recreating the Postgres container.** Cause: Docker Desktop had not yet released the previous container's port forward. Fix: wait a few seconds and rerun `docker compose --env-file .env -f infra/docker-compose.yml up -d`. Verify: `docker ps` shows `127.0.0.1:5432->5432/tcp` and `(healthy)`.
- **`./mvnw` fails with `The JAVA_HOME environment variable is not defined correctly`.** Cause: `JAVA_HOME` pointed at `C:\Program Files\Java\jdk-17`, which does not exist. Fix: set the user environment variable `JAVA_HOME` to `C:\Program Files\Java\jdk-21.0.12.1` and open a new terminal. Verify: `./mvnw -v` shows Maven 3.9.16 and Java 21.0.12.1.
- **Backend fails to start with `Connector ... failed to start` / `java.io.IOException: Unable to establish loopback connection` / `SocketException: Invalid argument: connect`.** Cause: on this machine the JVM cannot create the Unix-domain socket files that NIO selectors need when they live under `C:\Users\<user>\AppData\Local` (where `%TEMP%` is). Tomcat, the JDK `HttpClient` and the JDK `HttpServer` are all affected; it also happens outside Claude's sandbox. The same probe succeeds with the socket directory under `C:\dev` or the user profile root, so something on this machine (often Windows Defender Controlled Folder Access or another security tool) blocks socket files in `AppData\Local`. Workaround: point the JVM at another folder, e.g. set the user environment variable `JDK_JAVA_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\dev\tmp` (create the folder first), or pass `-Djdk.net.unixdomain.tmpdir=...` to `java`/`spring-boot:run`. Verify: the backend logs `Tomcat started on port 8080`. Tests are unaffected (MockMvc does not start Tomcat; the backend uses the `HttpURLConnection` client).
