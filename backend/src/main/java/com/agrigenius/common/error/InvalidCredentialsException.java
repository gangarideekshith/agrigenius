package com.agrigenius.common.error;

import org.springframework.http.HttpStatus;

/** Login failed. One message for unknown number, wrong password and disabled account. */
public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Unauthorized", "Invalid mobile number or password.");
    }
}
