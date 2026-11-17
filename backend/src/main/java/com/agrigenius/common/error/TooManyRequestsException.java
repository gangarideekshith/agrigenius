package com.agrigenius.common.error;

import java.time.Duration;
import org.springframework.http.HttpStatus;

/** Too many attempts within the throttling window; carries the time until the client may retry. */
public class TooManyRequestsException extends ApiException {

    private static final Duration MIN_RETRY_AFTER = Duration.ofSeconds(1);

    private final Duration retryAfter;

    public TooManyRequestsException(Duration retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", "Too many requests",
                "Too many attempts. Please wait and try again.");
        this.retryAfter = retryAfter == null || retryAfter.compareTo(MIN_RETRY_AFTER) < 0 ? MIN_RETRY_AFTER : retryAfter;
    }

    /** Time until the client may retry; at least one second. */
    public Duration retryAfter() {
        return retryAfter;
    }
}
