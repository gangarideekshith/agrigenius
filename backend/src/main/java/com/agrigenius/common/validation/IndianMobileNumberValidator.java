package com.agrigenius.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class IndianMobileNumberValidator implements ConstraintValidator<IndianMobileNumber, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || MobileNumbers.isValid(value);
    }
}
