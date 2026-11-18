package com.agrigenius.reminder.application;

import com.agrigenius.common.error.ApiException;
import org.springframework.http.HttpStatus;

/** Expected reminder rule violations with stable, client-translatable codes. */
public class ReminderException extends ApiException {

    private ReminderException(HttpStatus status, String code, String title, String detail) {
        super(status, code, title, detail);
    }

    static ReminderException inPast() {
        return new ReminderException(HttpStatus.BAD_REQUEST, "REMINDER_IN_PAST", "Time is in the past",
                "Choose a date and time in the future.");
    }

    static ReminderException tooFarAhead(int days) {
        return new ReminderException(HttpStatus.BAD_REQUEST, "REMINDER_TOO_FAR_AHEAD", "Time is too far ahead",
                "Reminders can be set at most " + days + " days ahead.");
    }

    static ReminderException invalidTimeZone() {
        return new ReminderException(HttpStatus.BAD_REQUEST, "INVALID_TIME_ZONE", "Invalid time zone",
                "Time zone must be an IANA zone such as Asia/Kolkata.");
    }

    static ReminderException invalidDateRange() {
        return new ReminderException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "Invalid date range",
                "The 'from' date must not be after the 'to' date.");
    }

    static ReminderException notEditable() {
        return new ReminderException(HttpStatus.CONFLICT, "REMINDER_NOT_EDITABLE", "Reminder cannot be edited",
                "Only pending reminders can be edited. Reopen it first.");
    }

    static ReminderException invalidTransition(String detail) {
        return new ReminderException(HttpStatus.CONFLICT, "REMINDER_INVALID_STATE", "Action not allowed", detail);
    }

    static ReminderException limitReached(int limit) {
        return new ReminderException(HttpStatus.CONFLICT, "REMINDER_LIMIT_REACHED", "Too many pending reminders",
                "You can have at most " + limit + " pending reminders. Complete or delete some first.");
    }
}
