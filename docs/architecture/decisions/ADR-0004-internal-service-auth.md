# ADR-0004: Authenticate Spring Boot to the ML service with a shared internal API key

- Status: Accepted
- Date: 2026-11-17
- Deciders: project owner (crop recommendation plan, Q6)

## Context

The Python ML service must stay private: only the Spring Boot backend may call it, and the browser never does (CLAUDE.md, ADR-0001). Until now the only protection was that the ML service listens on `127.0.0.1:8001`. Any local process could still call it, and the first inference endpoint (`POST /v1/crop-recommendation/predict`) is about to be added. Earlier status reports listed "service-to-service authentication" as a blocker for any inference endpoint.

Both services currently run on the same host.

## Decision

- Every request from the backend to the ML service carries an `X-Internal-Api-Key` header.
- The backend reads the key from `ML_SERVICE_API_KEY`; the ML service reads it from `ML_INTERNAL_API_KEY`. **The two values must be identical.**
- The key must be at least 32 characters (generate with `openssl rand -base64 48`). **Both services refuse to start** if their key is missing or too short.
- The ML service compares keys in constant time (`hmac.compare_digest`) and answers a missing or wrong key with `401 {"code":"UNAUTHORIZED"}`, without saying which.
- `/health` and `/health/ready` stay unauthenticated (liveness and readiness only, no data). All `/v1/**` routes require the key.
- The key is never logged by either side and never sent to the browser.
- Loopback binding (`ML_HOST=127.0.0.1`) remains the first line of defence.

## Alternatives considered

- **Mutual TLS:** strongest, but needs a local CA, certificates and rotation tooling; disproportionate while both services share one host.
- **Network policy only** (loopback or a private network): no protection against other local processes or a misconfigured bind address.
- **JWT client credentials** (backend signs a short-lived token): more moving parts (key distribution, clock skew) for little gain over a shared secret at this stage.

## Consequences

- Rotating the key means changing both values and restarting both services; there is no overlap window.
- A leaked key grants full ML API access; mitigated by loopback binding and by keeping the key out of logs and source.
- When the services move to separate hosts or containers on a shared network, revisit this ADR and move to mTLS (or a service mesh).
