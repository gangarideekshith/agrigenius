package com.agrigenius.notification.application;

import com.agrigenius.notification.api.dto.NotificationResponse;
import com.agrigenius.notification.domain.Notification;
import com.agrigenius.notification.infrastructure.NotificationRepository;
import com.agrigenius.recommendation.api.dto.PageResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** In-app notifications. Reads and writes are always scoped to the signed-in user. */
@Service
public class NotificationService {

    static final int MAX_BODY = 300;

    private final NotificationRepository repository;
    private final Clock clock;

    public NotificationService(NotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Records a "reminder due" notification inside the caller's transaction. Returns false when one
     * already exists for this reminder and scheduled time, so retries and concurrent scanners never
     * create duplicates.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean recordReminderDue(UUID userId, UUID reminderId, String title, String description,
            Instant scheduledFor, Instant now) {
        return repository.insertReminderDue(UUID.randomUUID(), userId, title, truncate(description), reminderId,
                scheduledFor, now) == 1;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(UUID userId, boolean unreadOnly, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<Notification> result = unreadOnly
                ? repository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId, pageable)
                : repository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return new PageResponse<>(result.map(NotificationService::toResponse).getContent(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return repository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public NotificationResponse markRead(UUID userId, UUID id) {
        Notification notification = repository.findByIdAndUserId(id, userId)
                .orElseThrow(NotificationNotFoundException::new);
        notification.markRead(now());
        return toResponse(notification);
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return repository.markAllRead(userId, now());
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    static String truncate(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String stripped = text.strip();
        return stripped.length() <= MAX_BODY ? stripped : stripped.substring(0, MAX_BODY - 1) + "…";
    }

    static NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getReminderId(),
                n.getScheduledFor(), n.getCreatedAt(), n.getReadAt() != null, n.getReadAt());
    }
}
