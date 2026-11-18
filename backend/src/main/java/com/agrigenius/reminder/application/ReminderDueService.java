package com.agrigenius.reminder.application;

import com.agrigenius.notification.application.NotificationService;
import com.agrigenius.reminder.config.ReminderProperties;
import com.agrigenius.reminder.domain.Reminder;
import com.agrigenius.reminder.infrastructure.ReminderRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns due reminders into in-app notifications.
 *
 * <p>A reminder is due when it is PENDING, has not been notified for its current time and its time
 * has passed. Because the work queue is the database (not in-memory timers), reminders that fell due
 * while the backend was stopped are picked up on the next run after a restart. Completed and
 * cancelled reminders are never selected. Duplicates are prevented twice over: the selected rows are
 * locked ({@code FOR UPDATE SKIP LOCKED}) and the notification insert is idempotent per reminder and
 * scheduled time.
 */
@Service
public class ReminderDueService {

    private final ReminderRepository reminders;
    private final NotificationService notifications;
    private final ReminderProperties properties;
    private final Clock clock;

    public ReminderDueService(ReminderRepository reminders, NotificationService notifications,
            ReminderProperties properties, Clock clock) {
        this.reminders = reminders;
        this.notifications = notifications;
        this.properties = properties;
        this.clock = clock;
    }

    /** Processes one batch in its own transaction. Returns how many reminders were claimed. */
    @Transactional
    public BatchResult processBatch() {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        List<Reminder> due = reminders.lockDue(now, properties.scanner().batchSize());
        int created = 0;
        for (Reminder reminder : due) {
            if (notifications.recordReminderDue(reminder.getUserId(), reminder.getId(), reminder.getTitle(),
                    reminder.getDescription(), reminder.getScheduledAt(), now)) {
                created++;
            }
            reminder.markNotified(now);
        }
        return new BatchResult(due.size(), created);
    }

    /** Claimed reminders and the notifications actually created (fewer if some already existed). */
    public record BatchResult(int claimed, int created) {
    }
}
