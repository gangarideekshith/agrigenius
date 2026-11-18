package com.agrigenius.reminder.domain;

import java.time.Instant;

/** Validated, normalized editable fields of a reminder. */
public record ReminderDetails(String title, String description, ReminderCategory category, String cropName,
        Instant scheduledAt, String timeZone, ReminderPriority priority) {
}
