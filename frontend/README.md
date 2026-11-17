# AgriGenius Frontend

Angular 22 single-page app for farmers: sign-in and registration, profile dashboard, AI crop recommendations (form, results, history) and the AgriGenius chat assistant.

The browser talks **only** to the Spring Boot backend under `/api`. The private ML service is never called from the browser, and no secrets live in this app.

## Prerequisites

- Node.js 24 (`^22.22.3 || ^24.15.0 || >=26` required by Angular CLI 22) and npm 11.
- The backend running on `http://localhost:8080` for real data (see `backend/README.md`). Tests do not need it.

## Commands (from `frontend/`)

```bash
npm ci                                            # install exact locked versions
npx ng serve --proxy-config proxy.conf.json       # http://localhost:4200, /api -> localhost:8080
npx ng test --watch=false                         # Vitest (jsdom) unit and component tests
npx ng build                                      # production build into dist/frontend
```

`proxy.conf.json` forwards `/api` to the backend, so the app always calls relative `/api/...` URLs. The backend's `CORS_ALLOWED_ORIGINS` default (`http://localhost:4200`) also allows direct calls.

## Structure

```
src/app/
  shell.ts                 header, navigation, language switcher, sign-out, <router-outlet>
  app.config.ts            router (component input binding) + HttpClient with interceptors
  app.routes.ts            lazy-loaded pages, authGuard / guestGuard
  core/
    api/                   wire models, typed API services, ApiError (problem+json), error descriptions
    auth/                  AuthService (token in memory + sessionStorage), interceptors, guards
    i18n/                  I18nService + `t` pipe, JSON dictionaries in src/assets/i18n
    validation/            validators mirroring backend rules; server field errors -> controls
  shared/                  field error, loading and error states
  features/
    auth/                  /login, /register
    dashboard/             /dashboard
    recommendations/       /recommendations/new, /recommendations/:id, /recommendations (history)
    chat/                  /chat
```

## Security notes

- The access token is kept in memory and `sessionStorage` (cleared when the tab closes), never `localStorage`. The session ends automatically at token expiry; any 401 from a protected endpoint signs the user out.
- The bearer token is only attached to `/api/**` requests, never to `/api/auth/*` or other hosts.
- No `innerHTML` or `bypassSecurityTrust*`: chat answers are rendered as text; source links are shown only for `http(s)` URLs, with `rel="noopener noreferrer"`.
- Login only follows same-app `returnUrl` values (no open redirects).
- Server error `detail` text is never shown; users see translated messages by error `code`, plus a reference number for server errors.

## Languages

English (`en.json`) is complete. Hindi, Telugu, Kannada and Tamil are **DRAFT** translations covering the main screens (`"_status": "DRAFT - needs native-speaker review"` in each file); any missing key falls back to English. Have a native speaker review them before release. The chosen language is remembered in `localStorage` (not sensitive); otherwise the profile's preferred language is used.

## Accessibility and design

Mobile-first layout (navigation collapses below 768 px), labelled inputs with `aria-describedby` help and error text, `aria-invalid` on invalid fields, `role="alert"` / `aria-live` for async results and errors, focus moves to the main content on navigation, visible focus outlines, AA colour contrast (dark green `#1B5E20`, leaf green `#43A047`, white) and `prefers-reduced-motion` support.
