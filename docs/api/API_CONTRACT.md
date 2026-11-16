# API Contract – Initial Outline

Base path: `/api`. All authenticated endpoints must enforce role and resource ownership. Final DTOs, status codes and error schemas must be agreed before implementation and documented in OpenAPI.

| Area | Method | Path | Intent |
|---|---|---|---|
| Auth | POST | `/api/auth/register` | Register |
| Auth | POST | `/api/auth/login` | Login |
| Auth | POST | `/api/auth/refresh` | Refresh tokens |
| Auth | POST | `/api/auth/forgot-password` | Password recovery |
| Farmer | GET | `/api/farmers/me` | Current profile |
| Farmer | PUT | `/api/farmers/me` | Update profile |
| Farms | POST | `/api/farms` | Create farm |
| Farms | GET | `/api/farms` | List accessible farms |
| Farms | GET | `/api/farms/{id}` | Farm details |
| Farms | PUT | `/api/farms/{id}` | Update farm |
| Soil | POST | `/api/farms/{farmId}/soil` | Add soil record |
| Soil | GET | `/api/farms/{farmId}/soil` | List soil records |
| Recommendations | POST | `/api/recommendations` | Generate crop options |
| Recommendations | GET | `/api/recommendations/history` | Recommendation history |
| Weather | GET | `/api/weather/{farmId}` | Farm forecast |
| Weather | GET | `/api/weather/alerts` | Accessible alerts |
| Chat | POST | `/api/chat/messages` | Send message |
| Chat | GET | `/api/chat/sessions` | List sessions |
| Chat | GET | `/api/chat/sessions/{id}/messages` | Session messages |

## Contract conventions to finalize
- DTO request/response examples and validation.
- Pagination/sorting format.
- Standard error response and correlation ID.
- Authentication and refresh-token lifecycle.
- Idempotency for scheduled/provider-triggered operations.
- Versioning and deprecation policy.

## Internal ML service (private – backend-to-backend only)

Called only by Spring Boot through `ML_SERVICE_BASE_URL`. Never exposed to Angular or the public internet. No `/api` prefix. Service-to-service authentication is not implemented yet and is required before any inference endpoint is added.

| Method | Path | Auth | Intent | Status |
|---|---|---|---|---|
| GET | `/health` | None (loopback-bound) | Liveness: process is up and serving | Implemented, tested |

`GET /health` → `200 OK`

```json
{
  "status": "ok",
  "service": "agrigenius-ml-service",
  "version": "0.1.0",
  "environment": "local"
}
```

- `status` is always `"ok"` when the process answers; any non-200 or timeout means unhealthy.
- `environment` is one of `local`, `test`, `staging`, `production`.
- This is liveness only. It does not check models or dependencies; a readiness check will be added with the first model.
- OpenAPI is served at `/openapi.json` and `/docs` except when `ML_APP_ENV=production` (unless `ML_DOCS_ENABLED=true`).
