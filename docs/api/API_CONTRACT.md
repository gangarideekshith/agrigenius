# API Contract – Initial Outline

Base path: `/api`. All authenticated endpoints must enforce role and resource ownership. Final DTOs, status codes and error schemas must be agreed before implementation and documented in OpenAPI.

## Backend conventions (implemented in the foundation)

**Errors.** Every error is `application/problem+json` (RFC 9457):

```json
{
  "type": "about:blank",
  "title": "Invalid request",
  "status": 400,
  "detail": "Request validation failed.",
  "instance": "/api/farms",
  "code": "VALIDATION_FAILED",
  "requestId": "3f2c6a2e-6b1d-4c8e-9f0a-1d2e3f4a5b6c",
  "errors": [ { "field": "name", "message": "must not be blank" } ]
}
```

- `code` is a stable, machine-readable identifier (decision D7) so clients can show localized messages (English, Hindi, Telugu, Kannada, Tamil). Clients must branch on `code`, never on `title` or `detail`.
- `errors` appears only for validation failures (sorted by field).
- `500` responses never include exception messages or stack traces; quote `requestId` to find the log entry.
- `401` (not authenticated) and `403` (not allowed) use the same shape and do not reveal why.

| `code` | Status | Meaning |
|---|---|---|
| `VALIDATION_FAILED` | 400 | Body or parameter validation failed; see `errors` |
| `MALFORMED_REQUEST` | 400 | Body is not valid JSON or has the wrong types |
| `AUTHENTICATION_REQUIRED` | 401 | Missing, invalid or expired token; response has `WWW-Authenticate: Bearer` |
| `INVALID_CREDENTIALS` | 401 | Login failed (same for unknown number, wrong password, disabled account) |
| `ACCESS_DENIED` | 403 | Authenticated but the role is not allowed |
| `NOT_FOUND` | 404 | No such resource or path |
| `METHOD_NOT_ALLOWED` | 405 | HTTP method not supported for the path |
| `ACCOUNT_ALREADY_EXISTS` | 409 | Registration with a mobile number or email already in use |
| `CONFLICT` | 409 | Other data conflict |
| `TOO_MANY_REQUESTS` | 429 | Throttled; response has `Retry-After` (seconds) |
| `INTERNAL_ERROR` | 500 | Unexpected error |

**Request IDs.** Every response carries `X-Request-Id`. A client-supplied value is echoed if it matches `[A-Za-z0-9-]{1,64}`; otherwise a UUID is generated.

**Authentication.** JWT bearer tokens (ADR-0003). Send `Authorization: Bearer <accessToken>`. Everything under `/api/**` requires a token except `POST /api/auth/register` and `POST /api/auth/login`; a token sent to those two endpoints is ignored, so a stale token cannot block login. CORS is allowed only for origins in `CORS_ALLOWED_ORIGINS`.

**Operational endpoints (public, no `/api` prefix).**

| Method | Path | Response |
|---|---|---|
| GET | `/actuator/health` | `{"status":"UP"}` (no component details) |
| GET | `/actuator/health/liveness` | `{"status":"UP"}` |
| GET | `/actuator/health/readiness` | `{"status":"UP"}`; DOWN if the database is unavailable |
| GET | `/actuator/info` | `{"app":{"name":"AgriGenius Backend","description":"…"}}` |
| GET | `/v3/api-docs`, `/swagger-ui.html` | OpenAPI document and UI; `local` profile only |

Other actuator endpoints are not exposed (`401`).

| Area | Method | Path | Intent | Status |
|---|---|---|---|---|
| Auth | POST | `/api/auth/register` | Register a farmer | In progress (M2) |
| Auth | POST | `/api/auth/login` | Login | In progress (M2) |
| Auth | POST | `/api/auth/refresh` | Refresh tokens | Deferred (D1) |
| Auth | POST | `/api/auth/forgot-password` | Password recovery | Deferred (D1) |
| Farmer | GET | `/api/farmers/me` | Current profile | In progress (M2) |
| Farmer | PUT | `/api/farmers/me` | Update profile | Planned |
| Farms | POST | `/api/farms` | Create farm | Planned |
| Farms | GET | `/api/farms` | List accessible farms | Planned |
| Farms | GET | `/api/farms/{id}` | Farm details | Planned |
| Farms | PUT | `/api/farms/{id}` | Update farm | Planned |
| Soil | POST | `/api/farms/{farmId}/soil` | Add soil record | Planned |
| Soil | GET | `/api/farms/{farmId}/soil` | List soil records | Planned |
| Recommendations | POST | `/api/recommendations` | Generate crop options | Planned |
| Recommendations | GET | `/api/recommendations/history` | Recommendation history | Planned |
| Weather | GET | `/api/weather/{farmId}` | Farm forecast | Planned |
| Weather | GET | `/api/weather/alerts` | Accessible alerts | Planned |
| Chat | POST | `/api/chat/messages` | Send message | Planned |
| Chat | GET | `/api/chat/sessions` | List sessions | Planned |
| Chat | GET | `/api/chat/sessions/{id}/messages` | Session messages | Planned |

## Authentication and farmer profile (Milestone 2)

Only `FARMER` accounts can self-register. `ADMIN`, `AGRICULTURAL_EXPERT`, `BUYER` and `VENDOR` exist in the role model but have no public registration.

### `POST /api/auth/register` (public)

```json
{
  "fullName": "Ravi Kumar",
  "mobileNumber": "9876543210",
  "password": "s3cure-pass",
  "preferredLanguage": "te",
  "email": "ravi@example.com",
  "state": "Telangana",
  "district": "Warangal",
  "village": "Hanamkonda"
}
```

| Field | Required | Rule |
|---|---|---|
| `fullName` | Yes | Trimmed, 2–100 characters; Unicode letters (Indic scripts allowed), spaces and `. ' -` |
| `mobileNumber` | Yes | Indian mobile `^(\+91)?[6-9]\d{9}$` after removing spaces and dashes; stored as `+91XXXXXXXXXX` |
| `password` | Yes | 8–64 characters and at most 72 UTF-8 bytes; at least one letter and one digit; must not equal the mobile number |
| `preferredLanguage` | Yes | One of `en`, `hi`, `te`, `kn`, `ta` |
| `email` | No | Valid address, at most 254 characters; stored lowercase |
| `state`, `district`, `village` | No | Trimmed; at most 60 / 60 / 100 characters |
| `role`, `id`, `status` | — | Not part of the request; ignored if sent. The account is always `FARMER`. |

| Status | Body |
|---|---|
| `201 Created` | `FarmerProfileResponse`; header `Location: /api/farmers/me`. No token is returned: the client calls login next (D6). |
| `400` | `VALIDATION_FAILED` (with `errors`) or `MALFORMED_REQUEST` |
| `409` | `ACCOUNT_ALREADY_EXISTS`, including two simultaneous registrations for the same number |
| `429` | `TOO_MANY_REQUESTS`, header `Retry-After` |

### `POST /api/auth/login` (public)

```json
{ "mobileNumber": "9876543210", "password": "s3cure-pass" }
```

`200 OK`:

```json
{
  "accessToken": "eyJ…",
  "tokenType": "Bearer",
  "expiresIn": 1800,
  "user": { "id": "3f2c6a2e-6b1d-4c8e-9f0a-1d2e3f4a5b6c", "role": "FARMER" }
}
```

| Status | Body |
|---|---|
| `400` | `VALIDATION_FAILED` / `MALFORMED_REQUEST` |
| `401` | `INVALID_CREDENTIALS`: "Invalid mobile number or password." Identical for an unknown number, a wrong password and a disabled account. |
| `429` | `TOO_MANY_REQUESTS`, header `Retry-After` (5 failed logins per number or 20 auth requests per IP in 15 minutes) |

### `GET /api/farmers/me` (Bearer token, role `FARMER`)

`200 OK` (`FarmerProfileResponse`):

```json
{
  "id": "3f2c6a2e-6b1d-4c8e-9f0a-1d2e3f4a5b6c",
  "fullName": "Ravi Kumar",
  "mobileNumber": "+919876543210",
  "email": "ravi@example.com",
  "preferredLanguage": "te",
  "state": "Telangana",
  "district": "Warangal",
  "village": "Hanamkonda",
  "addressLine": null,
  "role": "FARMER",
  "createdAt": "2026-11-17T10:15:30Z"
}
```

| Status | Body |
|---|---|
| `401` | `AUTHENTICATION_REQUIRED`, header `WWW-Authenticate: Bearer`: no token, bad signature, expired, wrong issuer/audience, or the account is disabled or deleted |
| `403` | `ACCESS_DENIED`: valid token for another role (for example `ADMIN`) |

No response ever contains the password or its hash.

### Access token

HS256-signed JWT, valid for `JWT_ACCESS_TOKEN_MINUTES` (default 30). Claims: `iss` (`JWT_ISSUER`), `aud` (`agrigenius-api`), `sub` (user UUID), `roles` (for example `["FARMER"]`), `iat`, `nbf`, `exp`, `jti`. No personal data (no mobile number, email or name). The backend checks the signature, algorithm, `iss`, `aud`, `exp` and `nbf` (30 s clock skew) and requires `sub`, `roles` and `exp`. No refresh tokens yet (D1).

## Contract conventions to finalize
- Pagination/sorting format.
- Refresh-token lifecycle and password recovery (deferred, D1).
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
