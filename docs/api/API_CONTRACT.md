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
| Auth | POST | `/api/auth/register` | Register a farmer | Implemented (PR #3) |
| Auth | POST | `/api/auth/login` | Login | Implemented (PR #3) |
| Auth | POST | `/api/auth/refresh` | Refresh tokens | Deferred (D1) |
| Auth | POST | `/api/auth/forgot-password` | Password recovery | Deferred (D1) |
| Farmer | GET | `/api/farmers/me` | Current profile | Implemented (PR #3) |
| Farmer | PUT | `/api/farmers/me` | Update profile | Planned |
| Farms | POST | `/api/farms` | Create farm | Planned |
| Farms | GET | `/api/farms` | List accessible farms | Planned |
| Farms | GET | `/api/farms/{id}` | Farm details | Planned |
| Farms | PUT | `/api/farms/{id}` | Update farm | Planned |
| Soil | POST | `/api/farms/{farmId}/soil` | Add soil record | Planned |
| Soil | GET | `/api/farms/{farmId}/soil` | List soil records | Planned |
| Recommendations | POST | `/api/recommendations` | Generate crop options | In progress (M3) |
| Recommendations | GET | `/api/recommendations/history` | Recommendation history | In progress (M3) |
| Recommendations | GET | `/api/recommendations/{id}` | One recommendation (owner only) | In progress (M3) |
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

## Crop recommendation (Milestone 3)

All three endpoints require a Bearer token with role `FARMER` (`/api/recommendations/**`). The account id always comes from the token. Results are **demo output** from a model trained on a public sample dataset (ADR-0005); every response carries an advisory to confirm with a local agriculture officer or KVK. Design: ADR-0004 (internal service auth), ADR-0005 (model and dataset).

### `POST /api/recommendations` (FARMER; 30 requests per user per hour)

```json
{
  "soil":    { "nitrogenKgHa": 90, "phosphorusKgHa": 42, "potassiumKgHa": 43, "ph": 6.5,
               "soilType": "LOAMY", "moisturePct": 30 },
  "climate": { "temperatureC": 21, "humidityPct": 82, "rainfallMm": 203 },
  "season": "KHARIF", "irrigationAvailable": true, "previousCrop": "maize",
  "location": { "state": "Telangana", "district": "Warangal" }
}
```

| Field | Rule |
|---|---|
| `soil.nitrogenKgHa`, `soil.phosphorusKgHa`, `soil.potassiumKgHa` | Required, 0–300 |
| `soil.ph` | Required, 3.0–10.0 |
| `climate.temperatureC` | Required, −5 to 55 (average °C for the season) |
| `climate.humidityPct` | Required, 0–100 |
| `climate.rainfallMm` | Required, 0–5,000 (expected seasonal rainfall) |
| `soil.moisturePct` | Optional, 0–100 |
| `soil.soilType` | Optional: `SANDY`, `LOAMY`, `CLAY`, `SILT`, `BLACK`, `RED`, `LATERITE`, `ALLUVIAL` |
| `season` | Optional: `KHARIF`, `RABI`, `ZAID` |
| `irrigationAvailable` | Optional boolean |
| `previousCrop` | Optional; must be a known crop code |
| `location.state`, `location.district` | Optional; trimmed, up to 60 characters |

These are physically plausible ranges. A value outside the model's **training** range is not an error: it produces the `INPUT_OUTSIDE_TRAINING_RANGE` warning. Only the seven fields listed in `featuresUsedByModel` are used by the current model; the others are stored for traceability.

**201 Created**, `Location: /api/recommendations/{id}`:

```json
{
  "id": "8b0c2f1e-4a5d-4c3b-9e7f-1a2b3c4d5e6f", "status": "COMPLETED", "requestedAt": "2026-11-17T10:15:00Z",
  "model": { "name": "crop-recommendation-rf", "version": "1.0.0", "dataLabel": "DEMO_PUBLIC_DATASET" },
  "featuresUsedByModel": ["nitrogenKgHa","phosphorusKgHa","potassiumKgHa","ph","temperatureC","humidityPct","rainfallMm"],
  "candidates": [
    { "rank": 1, "crop": { "code": "rice", "name": "Rice" },
      "suitabilityScore": 87, "probability": 0.87, "confidence": "HIGH",
      "explanation": [
        { "feature": "rainfallMm", "value": 203, "typicalRange": [182, 299], "fit": "WITHIN" },
        { "feature": "humidityPct", "value": 82, "typicalRange": [80, 85], "fit": "WITHIN" } ],
      "economics": { "available": false, "reason": "No verified market or cost data yet." } }
  ],
  "warnings": [],
  "advisory": "Model trained on a public sample dataset. Confirm with your local agriculture officer or KVK before planting."
}
```

- `candidates` has up to 5 entries, ranked by calibrated probability. `suitabilityScore` = round(probability × 100).
- `confidence` per candidate: `HIGH` (probability ≥ 0.60), `MEDIUM` (≥ 0.30), `LOW` (< 0.30).
- `explanation[].typicalRange` is that crop's 5th–95th percentile in the training data; `fit` is `BELOW`, `WITHIN` or `ABOVE`. Explanations are data-derived, never free-text agronomic claims.
- `economics` is always `available: false` in this version (no verified market or cost data). Agronomic suitability is never ranked by profitability.

**Status values**

| Status | Meaning |
|---|---|
| `COMPLETED` | Prediction stored; top candidate is MEDIUM or HIGH confidence and all inputs are within the training range |
| `LOW_CONFIDENCE` | Prediction stored, but the top candidate is LOW confidence or an input is outside the training range; the advisory recommends expert confirmation |
| `FAILED` | The ML service was unavailable or returned an unusable response; the request is stored for traceability (visible in history, never returned as a 201) |

**Warning codes:** `INPUT_OUTSIDE_TRAINING_RANGE` (with `feature`), `LOW_MODEL_CONFIDENCE`.

**Errors**

| Status | `code` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Field rules above (with `errors[]`) |
| 400 | `MALFORMED_REQUEST` | Unreadable JSON |
| 401 | `AUTHENTICATION_REQUIRED` | Missing, invalid or expired token |
| 403 | `ACCESS_DENIED` | Valid token without role FARMER |
| 429 | `TOO_MANY_REQUESTS` | More than 30 recommendation requests per user per hour (`Retry-After`) |
| 502 | `ML_SERVICE_ERROR` | ML service answered with an invalid response (for example an unknown crop code) |
| 503 | `ML_SERVICE_UNAVAILABLE` | ML service down, timed out, or model not loaded |

### `GET /api/recommendations/history?page=0&size=20` (FARMER)

The caller's own recommendations, newest first. `size` is 1–50 (default 20).

```json
{
  "items": [
    { "id": "8b0c2f1e-4a5d-4c3b-9e7f-1a2b3c4d5e6f", "status": "COMPLETED", "requestedAt": "2026-11-17T10:15:00Z",
      "topCrop": { "code": "rice", "name": "Rice" }, "topConfidence": "HIGH" }
  ],
  "page": 0, "size": 20, "totalItems": 1, "totalPages": 1
}
```

`FAILED` entries have `topCrop` and `topConfidence` set to `null`.

### `GET /api/recommendations/{id}` (FARMER, owner only)

Returns the full recommendation in the same shape as the POST response. Another farmer's id, or an unknown id, returns **404 `NOT_FOUND`** (never 403), so ids cannot be probed.

## Contract conventions to finalize
- Pagination/sorting format.
- Refresh-token lifecycle and password recovery (deferred, D1).
- Idempotency for scheduled/provider-triggered operations.
- Versioning and deprecation policy.

## Internal ML service (private – backend-to-backend only)

Called only by Spring Boot through `ML_SERVICE_BASE_URL`. Never exposed to Angular or the public internet. No `/api` prefix. Bound to `127.0.0.1:8001`.

**Authentication (ADR-0004):** every `/v1/**` request must carry `X-Internal-Api-Key`. The backend sends `ML_SERVICE_API_KEY`; the ML service checks it against `ML_INTERNAL_API_KEY` (same value, at least 32 characters, constant-time comparison). Missing or wrong key → `401 {"code":"UNAUTHORIZED","message":"Missing or invalid internal API key."}`. Health endpoints need no key.

| Method | Path | Auth | Intent | Status |
|---|---|---|---|---|
| GET | `/health` | None (loopback-bound) | Liveness: process is up and serving | Implemented, tested |
| GET | `/health/ready` | None (loopback-bound) | Readiness: model loaded and checksum verified | In progress (M3) |
| POST | `/v1/crop-recommendation/predict` | `X-Internal-Api-Key` | Top-k crop prediction | In progress (M3) |
| GET | `/v1/crop-recommendation/model` | `X-Internal-Api-Key` | Model name, version, checksum, classes, feature ranges, metrics | In progress (M3) |

**Contract fixtures:** exact request, response and error examples live in [`docs/api/contracts/crop-recommendation/`](contracts/crop-recommendation/). Both the ML service tests (pytest) and the backend tests (JUnit) load them, so a change must update both implementations. JSON is snake_case.

### `POST /v1/crop-recommendation/predict`

- **Request** (`predict-request.json`): `request_id` (UUID, for log correlation), `top_k` (1–10; the backend sends 5) and `features` with `nitrogen`, `phosphorus`, `potassium`, `temperature_c`, `humidity_pct`, `ph`, `rainfall_mm` (all required numbers).
- **Response** (`predict-response.json`, `predict-response-low-confidence.json`): `model` (`name`, `version`, `sha256`, `data_label`), `predictions[]` (`crop`, `probability` 0–1, sorted descending; `explanation[]` with `feature`, `value`, `p05`, `p95`), `warnings[]` (`code`, optional `feature`) and `inference_ms`.
- **Errors:** `401 UNAUTHORIZED` (`error-unauthorized.json`), `422 INVALID_REQUEST` with `errors[]` (`error-validation.json`), `503 MODEL_NOT_LOADED` (`error-model-not-loaded.json`). Error bodies never contain stack traces.

### `GET /v1/crop-recommendation/model`

Returns `model-info-response.json`: name, version, SHA-256, data label, feature names, class labels, per-feature training ranges, metrics and training time. Used for troubleshooting and the backend's model-version checks.

### `GET /health` → `200 OK`

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
- This is liveness only. `GET /health/ready` reports readiness: `200` when the crop model is loaded and its SHA-256 verified, `503 {"code":"MODEL_NOT_LOADED"}` otherwise.
- OpenAPI is served at `/openapi.json` and `/docs` except when `ML_APP_ENV=production` (unless `ML_DOCS_ENABLED=true`).
