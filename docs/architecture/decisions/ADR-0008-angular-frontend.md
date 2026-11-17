# ADR-0008: Angular frontend

- Status: Accepted (2026-11-17). The UI deferred by decision Q7 was started at the owner's request.

## Decision

- **Angular 22.2.x** standalone application in `frontend/`, with lazy-loaded routes, Reactive Forms, `HttpClient` interceptors and route guards. Node 24.19 and Angular CLI 22 are verified on the development machine.
- **Development proxy:** `ng serve` proxies `/api` to the backend, so the browser talks only to Spring Boot. The ML service and the LLM provider are never reachable from Angular.
- **Token storage:** the access token is kept in memory and in `sessionStorage` (not `localStorage`), so it survives a page reload but not a closed tab.
  - **Trade-off:** any script running in the page (XSS) could read it.
  - **Mitigations:** Angular's default output escaping, no `innerHTML` with untrusted data, short 30-minute tokens, and a strict CSP when deployed.
  - HttpOnly cookies would require backend CSRF handling; they're revisited with refresh tokens (D1).
- **No third-party UI or i18n libraries.** An in-house, signal-based i18n service covers English, Hindi, Telugu, Kannada and Tamil. The **hi/te/kn/ta translations are DRAFT** pending native-speaker review and are labelled as such.
- **Design direction** (from CLAUDE.md): dark green, leaf green and white; responsive and accessible; loading, empty, error and success states on every screen.

## Consequences

- One more build and test toolchain (npm) in the repository.
- Translations must be reviewed before release.
