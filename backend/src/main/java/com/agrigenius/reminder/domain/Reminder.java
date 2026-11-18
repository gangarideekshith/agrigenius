package com.agrigenius.reminder.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A farmer's scheduled farm task. Owned by exactly one user ({@code userId}, never changed).
 * State checks live in {@code ReminderService}; these methods only apply the change.
 */
@Entity
@Table(name = "reminders")
public class Reminder {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private ReminderCategory category;

    @Column(name = "crop_name", length = 60)
    private String cropName;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "time_zone", nullable = false, length = 40)
    private String timeZone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReminderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    private ReminderPriority priority;

    @Column(name = "notified_at")
    private Instant notifiedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Reminder() {
        // for JPA
    }

    public Reminder(UUID id, UUID userId, ReminderDetails details, Instant now) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        apply(details);
        this.status = ReminderStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Replaces the editable fields. A new time clears {@code notifiedAt} so the new time notifies again. */
    public void update(ReminderDetails details, Instant now) {
        if (!details.scheduledAt().equals(scheduledAt)) {
            notifiedAt = null;
        }
        apply(details);
        updatedAt = now;
    }

    public void complete(Instant now) {
        status = ReminderStatus.COMPLETED;
        completedAt = now;
        updatedAt = now;
    }

    public void cancel(Instant now) {
        status = ReminderStatus.CANCELLED;
        completedAt = null;
        updatedAt = now;
    }

    /** Back to PENDING. {@code notifiedAt} is kept, so an already-notified time does not notify twice. */
    public void reopen(Instant now) {
        status = ReminderStatus.PENDING;
        completedAt = null;
        updatedAt = now;
    }

    public void markNotified(Instant now) {
        notifiedAt = now;
    }

    public boolean isOverdue(Instant now) {
        return status == ReminderStatus.PENDING && scheduledAt.isBefore(now);
    }

    private void apply(ReminderDetails details) {
        this.title = details.title();
        this.description = details.description();
        this.category = details.category();
        this.cropName = details.cropName();
        this.scheduledAt = details.scheduledAt();
        this.timeZone = details.timeZone();
        this.priority = details.priority();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public ReminderCategory getCategory() {
        return category;
    }

    public String getCropName() {
        return cropName;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public ReminderStatus getStatus() {
        return status;
    }

    public ReminderPriority getPriority() {
        return priority;
    }

    public Instant getNotifiedAt() {
        return notifiedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
