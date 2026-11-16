package com.agrigenius.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordDiffersFromMobileValidator
        implements ConstraintValidator<PasswordDiffersFromMobile, MobileAndPassword> {

    @Override
    public boolean isValid(MobileAndPassword value, ConstraintValidatorContext context) {
        if (value == null || value.password() == null || value.mobileNumber() == null) {
            return true;
        }
        String password = value.password();
        String mobile = value.mobileNumber();
        boolean same = password.equals(mobile)
                || MobileNumbers.normalize(mobile)
                        .map(normalized -> password.equals(normalized) || password.equals(normalized.substring(3)))
                        .orElse(false);
        if (!same) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("password")
                .addConstraintViolation();
        return false;
    }
}
