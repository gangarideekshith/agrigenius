package com.agrigenius.common.error;

import org.springframework.http.HttpStatus;

/**
 * Base class for expected business errors that map to a specific HTTP status and a stable,
 * machine-readable {@code code} (used by clients for localized messages).
 * The detail must be safe to show to end users.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String title;

    protected ApiException(HttpStatus status, String code, String title, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String title() {
        return title;
    }

    public String detail() {
        return getMessage();
    }
}
