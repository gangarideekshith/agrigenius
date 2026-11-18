package com.agrigenius.reminder.infrastructure;

import com.agrigenius.reminder.domain.Reminder;
import com.agrigenius.reminder.domain.ReminderStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReminderRepository extends JpaRepository<Reminder, UUID>, JpaSpecificationExecutor<Reminder> {

    /** Owner-scoped lookup: another farmer's reminder is indistinguishable from a missing one. */
    Optional<Reminder> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndStatus(UUID userId, ReminderStatus status);

    long countByUserIdAndStatusAndScheduledAtBefore(UUID userId, ReminderStatus status, Instant before);

    long countByUserIdAndStatusAndScheduledAtGreaterThanEqualAndScheduledAtLessThan(
            UUID userId, ReminderStatus status, Instant from, Instant to);

    List<Reminder> findByUserIdAndStatusAndScheduledAtBeforeOrderByScheduledAtAsc(
            UUID userId, ReminderStatus status, Instant before, Pageable pageable);

    List<Reminder> findByUserIdAndStatusAndScheduledAtGreaterThanEqualAndScheduledAtLessThanOrderByScheduledAtAsc(
            UUID userId, ReminderStatus status, Instant from, Instant to, Pageable pageable);

    List<Reminder> findByUserIdAndStatusNotAndScheduledAtGreaterThanEqualAndScheduledAtLessThanOrderByScheduledAtAsc(
            UUID userId, ReminderStatus excluded, Instant from, Instant to, Pageable pageable);

    /**
     * The scanner's work queue: pending, not yet notified, due at or before {@code now}. Rows are locked
     * and already-locked rows skipped, so concurrent scanners (several backend instances) never
     * process the same reminder at the same time.
     */
    @Query(value = """
            select * from reminders
            where status = 'PENDING' and notified_at is null and scheduled_at <= :now
            order by scheduled_at
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<Reminder> lockDue(@Param("now") Instant now, @Param("limit") int limit);
}
