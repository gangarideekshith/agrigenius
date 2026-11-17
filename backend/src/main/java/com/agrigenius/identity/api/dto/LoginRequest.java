package com.agrigenius.identity.api.dto;

import com.agrigenius.common.validation.IndianMobileNumber;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Login by mobile number. Only shape is validated here; wrong credentials get one generic 401. */
public record LoginRequest(
        @NotBlank @IndianMobileNumber String mobileNumber,
        @NotBlank @Size(max = 128) String password) {

    public LoginRequest {
        mobileNumber = mobileNumber == null ? null : mobileNumber.strip();
    }

    @Override
    public String toString() {
        return "LoginRequest[password=<redacted>]";
    }
}
