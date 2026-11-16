# ADR-0003: JWT bearer authentication for farmers

- Status: Accepted
- Date: 2026-11-17
- Deciders: project owner (decisions D1–D8 approved in the authentication planning session)

## Context

Milestone 2 starts with farmer registration, login and `GET /api/farmers/me`. The backend is stateless (no sessions, CSRF off) and the Angular client will call it with an `Authorization` header. Requirements: never store or return plaintext passwords or hashes, keep signing secrets out of source, check token signature, expiry and claims, protect every endpoint by default, and support five roles while only farmers self-register. `docs/PROJECT_SPEC.md` §3.1 also lists password recovery.

## Decision

- **Tokens.** Short-lived JWT access tokens issued and verified with Spring Security's OAuth2 resource-server support (`NimbusJwtEncoder` / `NimbusJwtDecoder`, Nimbus JOSE + JWT). HS256 with a shared secret from `JWT_SECRET`, which must be base64 for at least 32 bytes; startup fails if it is missing, short, not base64 or the placeholder.
- **Claims.** `iss`, `aud` (`agrigenius-api`), `sub` (user UUID), `roles`, `iat`, `nbf`, `exp`, `jti`. No personal data. The decoder accepts only HS256 and checks the signature, `iss`, `aud`, `exp` and `nbf` (30 s clock skew) and requires `sub`, `roles` and `exp`.
- **Lifetime (D3).** 30 minutes (`JWT_ACCESS_TOKEN_MINUTES`, 1–120).
- **Deferred (D1).** Refresh tokens and password recovery move to the next authentication milestone, although PROJECT_SPEC §3.1 lists password recovery.
- **Login identifier (D2).** Indian mobile number, normalised to `+91XXXXXXXXXX`; email optional.
- **Roles (D4).** One role per user in `users.role` (`FARMER`, `ADMIN`, `AGRICULTURAL_EXPERT`, `BUYER`, `VENDOR`), mapped to `ROLE_*` authorities from the `roles` claim. Registration has no role field; the server always assigns `FARMER`.
- **Passwords.** BCrypt cost 12 through a `DelegatingPasswordEncoder` (stored as `{bcrypt}…`), so the algorithm can be upgraded later. Login uses `DaoAuthenticationProvider`, which runs a dummy hash check for unknown numbers.
- **Brute force (D5).** In-memory throttling: 5 failed logins per mobile number and 20 auth requests per IP per 15 minutes → `429` with `Retry-After`. No account lockout (it would let attackers lock out real farmers).
- **Registration (D6).** Returns `201` with the profile and no token; the client logs in next, so tokens are issued in one place.
- **Errors (D7).** Problem+json responses gain a stable `code` for client localization.
- **Database (D8).** The application connects as a least-privilege role (`DB_USERNAME`) with data-only grants; Flyway connects as the schema owner and its `afterMigrate.sql` callback maintains the role.

## Alternatives considered

- **jjwt with a custom filter:** another dependency not managed by Spring Boot and a hand-written authentication filter; rejected in favour of the framework's tested resource-server path.
- **RS256 key pair:** lets other services verify tokens without the signing key. Unnecessary while the backend is the only issuer and verifier; can be adopted later without changing clients.
- **Server-side sessions or opaque tokens:** instant revocation but needs session storage and CSRF protection; conflicts with the stateless design.

## Consequences

- Tokens stay valid until they expire. `GET /api/farmers/me` re-checks that the account exists and is `ACTIVE`; other endpoints must do the same where it matters.
- Throttling is per instance and resets on restart. Running more than one instance needs a shared store (for example Redis).
- The client IP comes from `request.getRemoteAddr()`. Behind a reverse proxy, forwarded headers must be configured deliberately before IP throttling is meaningful.
- Bearer tokens are ignored on `/api/auth/**`, so a stale token in the client does not break login or registration.
- Every environment must provide `JWT_SECRET`, `DB_USERNAME` and `DB_PASSWORD`; the backend refuses to start without them.
- Registration returns `409` for a number that is already registered, which reveals that the number has an account. This is unavoidable without OTP verification; throttling limits it.
