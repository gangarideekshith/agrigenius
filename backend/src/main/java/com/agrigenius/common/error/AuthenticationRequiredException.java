package com.agrigenius.common.error;

import org.springframework.http.HttpStatus;

/**
 * The token was valid but no longer maps to an active account (disabled, deleted, or missing
 * profile). Answered exactly like a missing token so nothing about the account is revealed.
 */
public class AuthenticationRequiredException extends ApiException {

    public AuthenticationRequiredException() {
        super(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Unauthorized",
                "Authentication is required to access this resource.");
    }
}
