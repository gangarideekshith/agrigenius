package com.agrigenius.reminder.application;

import com.agrigenius.reminder.domain.ReminderCategory;
import com.agrigenius.reminder.domain.ReminderStatus;
import java.time.LocalDate;

/** Optional list filters; every null field means "any". Dates are inclusive, local to {@code timeZone}. */
public record ReminderFilter(ReminderStatus status, ReminderCategory category, LocalDate from, LocalDate to,
        String query, String timeZone) {
}
