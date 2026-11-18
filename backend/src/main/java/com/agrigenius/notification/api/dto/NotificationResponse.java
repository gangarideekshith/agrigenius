package com.agrigenius.notification.api.dto;

import com.agrigenius.notification.domain.NotificationType;
import java.time.Instant;
import java.util.UUID;

/** {@code reminderId} links a REMINDER_DUE notification to its reminder (null if it was deleted). */
public record NotificationResponse(UUID id, NotificationType type, String title, String body, UUID reminderId,
        Instant scheduledFor, Instant createdAt, boolean read, Instant readAt) {
}
