package com.agrigenius.notification.application;

import com.agrigenius.common.error.ApiException;
import org.springframework.http.HttpStatus;

/** Unknown id, or an id belonging to another user (deliberately indistinguishable). */
public class NotificationNotFoundException extends ApiException {

    public NotificationNotFoundException() {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found", "Notification not found.");
    }
}
