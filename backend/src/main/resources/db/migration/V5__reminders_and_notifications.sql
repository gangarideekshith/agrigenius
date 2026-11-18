-- Agricultural alarm & reminder system (docs/AGRI_GENIUS_ALARM_SYSTEM.md).
-- reminders: farmer-owned scheduled farm tasks. scheduled_at is the absolute instant; time_zone is
-- the IANA zone the farmer picked the local date/time in (used to show it back and for "today").
-- notified_at is set by the due-reminder scanner once an in-app notification has been created;
-- it is cleared when the reminder is rescheduled so the new time can notify again.
-- notifications: in-app notifications. UNIQUE (reminder_id, scheduled_for) guarantees at most one
-- notification per reminder per scheduled time, even with concurrent scanners or restarts.

CREATE TABLE reminders (
    id            UUID PRIMARY KEY,
    user_id       UUID          NOT NULL
        CONSTRAINT fk_reminders_user REFERENCES users (id) ON DELETE CASCADE,
    title         VARCHAR(120)  NOT NULL,
    description   VARCHAR(1000),
    category      VARCHAR(30)   NOT NULL
        CONSTRAINT ck_reminders_category CHECK (category IN ('IRRIGATION', 'FERTILIZER', 'PESTICIDE', 'WEEDING',
            'CROP_INSPECTION', 'SOIL_TESTING', 'HARVESTING', 'SOWING', 'GENERAL', 'CUSTOM')),
    crop_name     VARCHAR(60),
    scheduled_at  TIMESTAMPTZ   NOT NULL,
    time_zone     VARCHAR(40)   NOT NULL DEFAULT 'Asia/Kolkata',
    status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
        CONSTRAINT ck_reminders_status CHECK (status IN ('PENDING', 'COMPLETED', 'CANCELLED')),
    priority      VARCHAR(10)   NOT NULL DEFAULT 'MEDIUM'
        CONSTRAINT ck_reminders_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    notified_at   TIMESTAMPTZ,
    completed_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ   NOT NULL,
    updated_at    TIMESTAMPTZ   NOT NULL,
    version       BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT ck_reminders_title_not_blank CHECK (length(btrim(title)) > 0),
    CONSTRAINT ck_reminders_completed_at CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL))
);

-- Owner listings ordered by time, and per-owner status filters / counts.
CREATE INDEX ix_reminders_user_scheduled ON reminders (user_id, scheduled_at);
CREATE INDEX ix_reminders_user_status_scheduled ON reminders (user_id, status, scheduled_at);
-- The scanner's work queue: pending reminders not yet notified, by due time.
CREATE INDEX ix_reminders_due ON reminders (scheduled_at)
    WHERE status = 'PENDING' AND notified_at IS NULL;

CREATE TABLE notifications (
    id             UUID PRIMARY KEY,
    user_id        UUID          NOT NULL
        CONSTRAINT fk_notifications_user REFERENCES users (id) ON DELETE CASCADE,
    type           VARCHAR(30)   NOT NULL
        CONSTRAINT ck_notifications_type CHECK (type IN ('REMINDER_DUE')),
    title          VARCHAR(120)  NOT NULL,
    body           VARCHAR(300),
    reminder_id    UUID
        CONSTRAINT fk_notifications_reminder REFERENCES reminders (id) ON DELETE CASCADE,
    scheduled_for  TIMESTAMPTZ,
    created_at     TIMESTAMPTZ   NOT NULL,
    read_at        TIMESTAMPTZ,
    CONSTRAINT uq_notifications_reminder_time UNIQUE (reminder_id, scheduled_for)
);

CREATE INDEX ix_notifications_user_created ON notifications (user_id, created_at DESC);
CREATE INDEX ix_notifications_user_unread ON notifications (user_id) WHERE read_at IS NULL;
