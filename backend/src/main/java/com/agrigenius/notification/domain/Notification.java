package com.agrigenius.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** An in-app notification for one user. Rows are inserted by {@code NotificationRepository#insertReminderDue}. */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30, updatable = false)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 120, updatable = false)
    private String title;

    @Column(name = "body", length = 300, updatable = false)
    private String body;

    @Column(name = "reminder_id", updatable = false)
    private UUID reminderId;

    @Column(name = "scheduled_for", updatable = false)
    private Instant scheduledFor;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
        // for JPA
    }

    /** Idempotent: keeps the first read time. */
    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public UUID getReminderId() {
        return reminderId;
    }

    public Instant getScheduledFor() {
        return scheduledFor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
