package com.agrigenius.reminder.api.dto;

/**
 * Counts for the signed-in farmer. {@code overdue}, {@code dueToday} and {@code upcoming} count PENDING
 * reminders only. {@code dueToday} is every pending reminder scheduled on today's date in the requested
 * time zone (earlier ones are also overdue); {@code upcoming} is the next {@code upcomingDays} days after now.
 */
public record ReminderSummaryResponse(long pending, long overdue, long dueToday, long upcoming, int upcomingDays,
        long completed, long cancelled) {
}
