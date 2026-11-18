package com.agrigenius.reminder.application;

import com.agrigenius.common.error.ApiException;
import org.springframework.http.HttpStatus;

/** Unknown id, or an id belonging to another farmer (deliberately indistinguishable). */
public class ReminderNotFoundException extends ApiException {

    public ReminderNotFoundException() {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found", "Reminder not found.");
    }
}
