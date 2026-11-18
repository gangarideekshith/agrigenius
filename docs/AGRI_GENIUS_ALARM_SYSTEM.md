# AgriGenius – Agricultural Alarm & Reminder System

Farmers plan farm work (watering, fertilizer, spraying, weeding, inspection, soil testing, sowing, harvest) as **reminders**. When a reminder's time arrives, the backend creates an **in-app notification**, which the Angular app shows under a bell in the header.

> **What "alarm" means here.** Alerts appear **only while AgriGenius is open in a browser tab**. Nothing rings when the browser is closed: there is no push service, service worker, SMS or app. See [Limitations](#limitations).

## 1. Architecture

```mermaid
flowchart LR
    subgraph Browser["Angular app (browser)"]
        UI[Reminders pages<br/>/reminders, /reminders/new, /reminders/:id]
        Bell[Notification bell<br/>polls unread count every 60 s]
    end
    subgraph Backend["Spring Boot (modular monolith)"]
        RC[ReminderController<br/>/api/reminders/**]
        NC[NotificationController<br/>/api/notifications/**]
        RS[ReminderService]
        NS[NotificationService]
        SC[ReminderDueScanner<br/>@Scheduled, every 1 min]
        DS[ReminderDueService]
    end
    DB[(PostgreSQL<br/>reminders, notifications)]
    UI -- JWT --> RC --> RS --> DB
    Bell -- JWT --> NC --> NS --> DB
    SC --> DS --> DB
    DS --> NS
```

| Layer | Package / file |
|---|---|
| Domain | `reminder.domain` (`Reminder`, `ReminderCategory`, `ReminderStatus`, `ReminderPriority`, `ReminderDetails`), `notification.domain` (`Notification`, `NotificationType`) |
| Persistence | `ReminderRepository` (Spring Data JPA + `JpaSpecificationExecutor`), `NotificationRepository` |
| Use cases | `ReminderService` (CRUD, state changes, views), `NotificationService`, `ReminderDueService` (due detection) |
| Scheduling | `ReminderDueScanner` + `ReminderSchedulingConfig` (`@EnableScheduling`, only when the scanner is enabled) |
| API | `ReminderController`, `NotificationController`, DTOs under `*.api.dto` |
| Config | `ReminderProperties` (`agrigenius.reminders.*`) |
| Frontend | `features/reminders/*`, `core/notifications/notification-center.ts`, `shared/notification-bell.ts`, `ReminderApi`/`NotificationApi` in `core/api/api.services.ts` |

There are no new services or dependencies. Everything runs inside the existing backend, and the ML service is untouched.

## 2. Database (Flyway `V5__reminders_and_notifications.sql`)

### `reminders`

| Column | Type | Notes |
|---|---|---|
| `id` | uuid PK | |
| `user_id` | uuid FK → `users` ON DELETE CASCADE | the owner; set from the JWT, never from the request |
| `title` | varchar(120) not null | must not be blank (CHECK) |
| `description` | varchar(1000) | |
| `category` | varchar(30) | CHECK: IRRIGATION, FERTILIZER, PESTICIDE, WEEDING, CROP_INSPECTION, SOIL_TESTING, HARVESTING, SOWING, GENERAL, CUSTOM |
| `crop_name` | varchar(60) | free text; there is no farms table yet |
| `scheduled_at` | timestamptz not null | the absolute instant |
| `time_zone` | varchar(40) default `Asia/Kolkata` | IANA zone the farmer chose the local date/time in |
| `status` | varchar(20) | CHECK: PENDING, COMPLETED, CANCELLED |
| `priority` | varchar(10) | CHECK: LOW, MEDIUM, HIGH |
| `notified_at` | timestamptz | set by the scanner; cleared when the time is changed |
| `completed_at` | timestamptz | CHECK: set if and only if status = COMPLETED |
| `created_at`, `updated_at` | timestamptz | |
| `version` | bigint | optimistic locking |

Indexes:
- `(user_id, scheduled_at)` for owner lists.
- `(user_id, status, scheduled_at)` for status views and counts.
- A partial index `ix_reminders_due (scheduled_at) WHERE status='PENDING' AND notified_at IS NULL`. This is the scanner's work queue.

### `notifications`

| Column | Type | Notes |
|---|---|---|
| `id` | uuid PK | |
| `user_id` | uuid FK → `users` ON DELETE CASCADE | |
| `type` | varchar(30) | CHECK: `REMINDER_DUE` (room for future types such as weather alerts) |
| `title` / `body` | varchar(120) / varchar(300) | a snapshot of the reminder title and notes |
| `reminder_id` | uuid FK → `reminders` ON DELETE CASCADE | deleting a reminder deletes its notifications |
| `scheduled_for` | timestamptz | the reminder time that triggered it |
| `created_at`, `read_at` | timestamptz | `read_at` null means unread |

The constraint **`UNIQUE (reminder_id, scheduled_for)`** guarantees at most one notification per reminder per scheduled time. Indexes: `(user_id, created_at DESC)` and a partial index on `(user_id) WHERE read_at IS NULL` for the unread count.

The least-privilege app role gets data access automatically through the existing `afterMigrate.sql` grants. Existing migrations are unchanged.

## 3. REST API

All endpoints need `Authorization: Bearer <token>` and role `FARMER`. The farmer is **always** the token subject. Request bodies have no owner field, and unknown JSON fields (for example a `userId`) are ignored. Another farmer's reminder or notification id returns **404**, exactly like a missing id. Errors use the existing problem+json format with a `code` and `requestId`.

### Reminders: `/api/reminders`

| Method & path | Purpose | Success |
|---|---|---|
| `POST /api/reminders` | Create | 201 + `Location`, `ReminderResponse` |
| `GET /api/reminders?status&category&from&to&q&timeZone&page&size` | Filtered, paged list, earliest first (`size` 1–50, default 20) | 200 `Page<ReminderResponse>` |
| `GET /api/reminders/{id}` | One reminder | 200 |
| `PUT /api/reminders/{id}` | Replace the editable fields (PENDING only) | 200 |
| `DELETE /api/reminders/{id}` | Delete (and its notifications) | 204 |
| `POST /api/reminders/{id}/complete` | PENDING → COMPLETED (repeat = no-op) | 200 |
| `POST /api/reminders/{id}/cancel` | PENDING → CANCELLED (repeat = no-op) | 200 |
| `POST /api/reminders/{id}/reopen` | COMPLETED/CANCELLED → PENDING | 200 |
| `GET /api/reminders/today?timeZone` | Today's non-cancelled reminders in that zone | 200 `ReminderResponse[]` (max 100) |
| `GET /api/reminders/upcoming?days` | PENDING from now to now + days (default 7, max 60) | 200 `ReminderResponse[]` |
| `GET /api/reminders/overdue` | PENDING whose time has passed, oldest first | 200 `ReminderResponse[]` |
| `GET /api/reminders/summary?timeZone` | Counts for the dashboard | 200 `ReminderSummaryResponse` |

`from`/`to` are inclusive local dates (`YYYY-MM-DD`) in `timeZone`. `q` searches the title, crop and notes, case-insensitively, with LIKE wildcards escaped.

**Request (`ReminderRequest`):**
```json
{
  "title": "Irrigate paddy field",          // required, 1–120 chars (trimmed)
  "description": "Open the canal gate",     // optional, ≤ 1000
  "category": "IRRIGATION",                 // required, one of the categories above
  "cropName": "Rice",                       // optional, ≤ 60
  "scheduledDate": "2026-11-20",            // required, local date
  "scheduledTime": "06:30",                 // required, local time
  "timeZone": "Asia/Kolkata",               // optional IANA id (default Asia/Kolkata)
  "priority": "HIGH"                        // optional, default MEDIUM
}
```

**Response (`ReminderResponse`):** `id, title, description, category, cropName, scheduledAt (UTC instant), scheduledDate, scheduledTime, timeZone, status, priority, overdue, notifiedAt, completedAt, createdAt, updatedAt`. `overdue` is true for a PENDING reminder whose time has passed.

**Summary:** `pending, overdue, dueToday, upcoming, upcomingDays, completed, cancelled`. `dueToday` counts pending reminders on today's date, including any that are already overdue.

**Errors:**

| Status | `code` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Missing or blank title, missing date/time, too-long fields (with `errors[]` per field) |
| 400 | `MALFORMED_REQUEST` | Invalid JSON, unknown category/priority/status value, bad UUID or date parameter |
| 400 | `REMINDER_IN_PAST` | Time more than 2 minutes in the past (on create, or on update when the time changes) |
| 400 | `REMINDER_TOO_FAR_AHEAD` | More than 1095 days ahead |
| 400 | `INVALID_TIME_ZONE` | Not a known IANA zone id |
| 400 | `INVALID_DATE_RANGE` | `from` after `to` |
| 401 | `AUTHENTICATION_REQUIRED` | No or invalid token |
| 403 | `ACCESS_DENIED` | Authenticated but not a FARMER |
| 404 | `NOT_FOUND` | Unknown id or another farmer's reminder |
| 409 | `REMINDER_NOT_EDITABLE` | PUT on a completed or cancelled reminder |
| 409 | `REMINDER_INVALID_STATE` | Complete a cancelled reminder, or cancel a completed one |
| 409 | `REMINDER_LIMIT_REACHED` | More than 500 pending reminders (create or reopen) |

Editing an overdue reminder **without** changing its time is allowed (for example, to fix the title). Moving the time requires a future time.

### Notifications: `/api/notifications`

| Method & path | Purpose |
|---|---|
| `GET /api/notifications?unreadOnly=false&page=0&size=20` | Newest first, `size` 1–50 → `Page<NotificationResponse>` |
| `GET /api/notifications/unread-count` | `{ "count": n }`, polled by the bell |
| `POST /api/notifications/{id}/read` | Mark one read (idempotent) → `NotificationResponse` |
| `POST /api/notifications/read-all` | `{ "updated": n }` |

`NotificationResponse`: `id, type, title, body, reminderId, scheduledFor, createdAt, read, readAt`.

## 4. Scheduling and due detection

`ReminderDueScanner` runs on a fixed delay. The default is every minute (`REMINDER_SCAN_INTERVAL=PT1M`), starting 15 s after boot. Each run calls `ReminderDueService.processBatch()` repeatedly, up to 20 batches of 200. Each batch is its own transaction:

1. `SELECT … FROM reminders WHERE status='PENDING' AND notified_at IS NULL AND scheduled_at <= now ORDER BY scheduled_at LIMIT 200 FOR UPDATE SKIP LOCKED`
2. For each row: `INSERT INTO notifications … ON CONFLICT (reminder_id, scheduled_for) DO NOTHING`.
3. Set `notified_at = now`.

| Requirement | How it is met |
|---|---|
| No duplicate notifications | The `notified_at` marker, row locks with `SKIP LOCKED` (safe with several backend instances), and the unique constraint as a final guard |
| Restarts and missed schedules | The queue is the database, not in-memory timers. Anything that fell due while the backend was down is picked up on the first run after start-up, and appears with an "overdue" marker. |
| Never trigger for completed or cancelled | The query selects `status='PENDING'` only, and the row lock serialises it against a concurrent complete or cancel |
| Rescheduling | Changing the time clears `notified_at`, so the new time notifies once. Reopening keeps `notified_at`, so an already-notified time does not notify twice. |
| Failure | A failing run is logged and retried on the next run. Nothing is marked notified unless its transaction commits. |

Latency: a notification appears at most about one scan interval after its time, plus up to one bell poll (60 s) before the browser shows it.

## 5. Notifications in the app

- **Bell** (header, signed-in only):
  - Polls `unread-count` every 60 s while the app is open, starting at sign-in and stopping at sign-out.
  - Shows a badge, and a panel with the latest 10 notifications.
  - Each notification can be marked read, or opened: opening marks it read and goes to the reminder.
  - Also has "Mark all as read", empty, loading and error states, and closes on Escape or a click outside.
- **Overdue** reminders have a red edge and an "Overdue" chip in lists. The detail page shows a notice.
- **Completed** reminders stay visible (the Completed tab, and today's list on the dashboard) with strike-through.
- **Optional device alerts:**
  - "Turn on device alerts" in the panel asks the browser for notification permission, and only on a click.
  - Once allowed, each **new** notification found by a poll is also shown as a system notification.
  - This works only while an AgriGenius tab is open. The preference is stored in `localStorage` (`agrigenius.browserNotifications`).

## 6. Authentication and authorization

- `SecurityConfig` adds `/api/reminders/**` and `/api/notifications/**` as `hasRole("FARMER")`.
- Every repository read and write in a request is scoped by `user_id` from the JWT (`findByIdAndUserId`, `userId` predicates, `markAllRead(userId)`). The scanner is the only code that reads across users, and it never exposes data over HTTP.
- No farmer id is accepted from the client.

## 7. Frontend

| Route | Page |
|---|---|
| `/reminders` | Reminders dashboard: count tiles (overdue, due today, next 7 days, pending, completed) and tabs. Overdue, Today and Upcoming are fixed views; Pending, Completed and All are lists with filters for category, date range, text and status. Each reminder has quick actions: mark done, edit, reopen, delete (with inline confirmation). The selected tab is kept in `?view=`. |
| `/reminders/new` | Form: title, type of work, priority, crop, date and time pickers, and notes. It has client validation (required fields, lengths, no past time), shows server field errors, has a saving state, and offers Cancel. New reminders use the browser time zone. |
| `/reminders/:id/edit` | The same form, prefilled. It keeps the reminder's own time zone, and explains if the reminder is not editable. |
| `/reminders/:id` | All details, plus: mark done, edit, cancel, reopen and delete (with confirmation). Shows a success message after saving. |
| `/dashboard` | "Farm reminders" card (overdue, today and upcoming counts with links, and today's list) plus a Reminders tile |

Strings are in `assets/i18n/en.json` under `reminders.*`, `notifications.*`, `dashboard.reminders*` and the new `errors.*` codes. Hindi, Telugu, Kannada and Tamil fall back to English for these keys until they are translated.

## 8. Configuration

`application.yml`, `agrigenius.reminders.*`:

| Property | Env var | Default |
|---|---|---|
| `default-time-zone` | `REMINDER_DEFAULT_TIME_ZONE` | `Asia/Kolkata` |
| `scanner.enabled` | `REMINDER_SCANNER_ENABLED` | `true` (the test profile sets `false`) |
| `scanner.interval` | `REMINDER_SCAN_INTERVAL` | `PT1M` (ISO-8601 duration) |
| `scanner.initial-delay` | – | `PT15S` |
| `scanner.batch-size` / `max-batches-per-run` | – | 200 / 20 |
| `max-pending-per-farmer` | – | 500 |
| `max-days-ahead` | – | 1095 |
| `past-grace` | – | 2m |
| `upcoming-default-days` / `upcoming-max-days` | – | 7 / 60 |
| `list-limit` | – | 100 (max items from today, upcoming and overdue) |

No secrets are involved.

## 9. Running locally

Nothing new needs installing. Follow `RUN_AGRIGENIUS.txt` / `docs/engineering/DEVELOPMENT.md`. The first backend start after pulling applies migration V5 automatically, and existing data is kept.

```powershell
docker compose --env-file .env -f infra/docker-compose.yml up -d
cd backend;  .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
cd frontend; $env:NG_CLI_ANALYTICS='false'; npx.cmd ng serve
```

Then open http://localhost:4200 → **Reminders**.

To try an alert quickly:
1. Create a reminder 2–3 minutes ahead.
2. Keep the tab open.
3. Within about 2 minutes of that time, the bell shows a badge.

## 10. Testing

| Suite | Covers |
|---|---|
| `ReminderIntegrationTest` (Testcontainers PostgreSQL) | CRUD; complete/reopen/cancel rules; ownership (404 for every endpoint); 401 without a token; validation (blank title, bad category, malformed JSON, past, too far, bad zone, long title, bad filters); editing overdue reminders; filters and the today/upcoming/overdue/summary views; due detection; one notification per due reminder across repeated scans; none for completed, cancelled or future reminders; rescheduling notifies again but reopening does not; notification list, unread count, mark read, ownership and read-all; cascade delete |
| `ReminderServiceTest` | Time-zone conversion, grace period, horizon, zone validation, pending limit, notified-marker reset, not-found, overdue flag |
| `ReminderDueScannerTest` | Batch draining, the per-run cap, and that failures do not escape the scheduler |
| `ApplicationIntegrationTest` | Migration list and table list now include V5 |
| `reminders.spec.ts` (Vitest) | Past-time helper; form create, edit, validation, past time, server field errors and the not-editable state; list counts, overdue view, empty, error and retry, filters, complete, delete with confirmation; detail reopen and not-found; bell badge, panel, open notification, empty state and the "browser closed" note |

```powershell
cd backend;  .\mvnw.cmd -B verify
cd frontend; npx.cmd ng test --watch=false; npx.cmd ng build
```

## 11. Limitations

- **No alerts when the browser is closed**, and none on a phone with the tab in the background if the browser suspends it. Device alerts use the browser `Notification` API from the open page. A real "ring when closed" needs Web Push with a service worker and VAPID keys, or FCM, SMS or IVR. That is planned future work (Firebase is already in the approved stack "when justified").
- **Polling, not WebSockets:** up to about 1 scan interval plus 1 poll interval (about 2 minutes) of delay.
- **No recurring reminders** (for example, "every 3 days"). Create them one by one for now.
- **No farm link:** reminders hold a free-text crop name because farms are not built yet. A `farm_id` column can be added with farm CRUD.
- **No snooze**, and no reminder sound inside the page.
- **Notification retention:** notifications are kept until their reminder or the account is deleted. There is no cleanup job yet.
- **Translations:** reminder strings are English-only so far; other languages fall back to English.
- Overlap with the Phase 2 "crop lifecycle/tasks" module: these reminders are standalone farmer tasks. Phase 2 can generate reminders from crop cycles later, using the same tables.
