package com.agrigenius.reminder.api.dto;

import com.agrigenius.reminder.domain.ReminderCategory;
import com.agrigenius.reminder.domain.ReminderPriority;
import com.agrigenius.reminder.domain.ReminderStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * {@code scheduledDate}/{@code scheduledTime} are local to {@code timeZone}; {@code scheduledAt} is the
 * same moment in UTC. {@code overdue} is true for a PENDING reminder whose time has passed.
 */
public record ReminderResponse(UUID id, String title, String description, ReminderCategory category,
        String cropName, Instant scheduledAt, LocalDate scheduledDate, LocalTime scheduledTime, String timeZone,
        ReminderStatus status, ReminderPriority priority, boolean overdue, Instant notifiedAt, Instant completedAt,
        Instant createdAt, Instant updatedAt) {
}
