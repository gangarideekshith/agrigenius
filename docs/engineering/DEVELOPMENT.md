# Development Environment and Commands

This document must contain only commands verified against the actual repository and machine. Replace pending values after setup.

## Environment baseline

- OS: To be inspected
- Java: Java 21 LTS preferred
- Spring Boot: compatible 3.x release selected during setup
- Node/npm/Angular CLI: inspect and pin compatible versions
- Python: inspect and pin supported version
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

## Verified commands

| Component | Install/build | Run | Test | Port |
|---|---|---|---|---|
| Angular | Pending | Pending | Pending | Pending |
| Spring Boot | Pending | Pending | Pending | Pending |
| PostgreSQL | Pending | Pending | Pending | Pending |
| Python ML | Pending | Pending | Pending | Pending |

## Troubleshooting log

Record the symptom, root cause, exact fix, and verification command for recurring setup problems.
