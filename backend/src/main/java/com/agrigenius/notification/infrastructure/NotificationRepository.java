package com.agrigenius.notification.infrastructure;

import com.agrigenius.notification.domain.Notification;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Notification> findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(UUID userId);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
    int markAllRead(@Param("userId") UUID userId, @Param("now") Instant now);

    /**
     * Creates the "reminder due" notification unless one already exists for this reminder and time
     * (uq_notifications_reminder_time). Returns 1 if inserted, 0 if it was a duplicate.
     */
    @Modifying
    @Query(value = """
            insert into notifications (id, user_id, type, title, body, reminder_id, scheduled_for, created_at)
            values (:id, :userId, 'REMINDER_DUE', :title, :body, :reminderId, :scheduledFor, :now)
            on conflict (reminder_id, scheduled_for) do nothing
            """, nativeQuery = true)
    int insertReminderDue(@Param("id") UUID id, @Param("userId") UUID userId, @Param("title") String title,
            @Param("body") String body, @Param("reminderId") UUID reminderId,
            @Param("scheduledFor") Instant scheduledFor, @Param("now") Instant now);
}
