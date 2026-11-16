package com.agrigenius.common.error;

import org.springframework.http.HttpStatus;

/** Registration conflict: the mobile number or email already belongs to an account. */
public class AccountAlreadyExistsException extends ApiException {

    public AccountAlreadyExistsException(String detail) {
        super(HttpStatus.CONFLICT, "ACCOUNT_ALREADY_EXISTS", "Account already exists", detail);
    }
}
