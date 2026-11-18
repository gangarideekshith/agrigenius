package com.agrigenius.reminder.domain;

/**
 * PENDING reminders are active (and can become due/overdue). COMPLETED and CANCELLED reminders never
 * notify. Allowed transitions: PENDING -> COMPLETED | CANCELLED, COMPLETED | CANCELLED -> PENDING (reopen).
 */
public enum ReminderStatus {
    PENDING,
    COMPLETED,
    CANCELLED
}
