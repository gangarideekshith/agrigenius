package com.agrigenius.reminder.application;

import com.agrigenius.reminder.config.ReminderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the due-reminder check on a fixed delay (agrigenius.reminders.scanner.interval, default one
 * minute), so a notification appears at most about one interval after its time. Each run drains due
 * reminders batch by batch, up to {@code maxBatchesPerRun}; anything left is taken by the next run.
 * Disabled with agrigenius.reminders.scanner.enabled=false (the test profile does this and calls
 * {@link ReminderDueService} directly).
 */
@Component
@ConditionalOnProperty(prefix = "agrigenius.reminders.scanner", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class ReminderDueScanner {

    private static final Logger log = LoggerFactory.getLogger(ReminderDueScanner.class);

    private final ReminderDueService service;
    private final ReminderProperties properties;

    public ReminderDueScanner(ReminderDueService service, ReminderProperties properties) {
        this.service = service;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${agrigenius.reminders.scanner.interval:PT1M}",
            initialDelayString = "${agrigenius.reminders.scanner.initial-delay:PT15S}")
    public void scan() {
        int claimed = 0;
        int created = 0;
        try {
            for (int i = 0; i < properties.scanner().maxBatchesPerRun(); i++) {
                ReminderDueService.BatchResult batch = service.processBatch();
                claimed += batch.claimed();
                created += batch.created();
                if (batch.claimed() < properties.scanner().batchSize()) {
                    break;
                }
            }
        } catch (RuntimeException ex) {
            // Nothing is lost: unprocessed reminders stay due and are retried on the next run.
            log.error("Due-reminder scan failed; will retry on the next run", ex);
        }
        if (claimed > 0) {
            log.info("Due-reminder scan: {} reminders due, {} notifications created", claimed, created);
        }
    }
}
