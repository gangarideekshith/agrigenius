package com.agrigenius.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class ValidPasswordValidator implements ConstraintValidator<ValidPassword, String> {

    static final String POLICY_MESSAGE = "must be 8-64 characters with at least one letter and one digit";
    static final String TOO_MANY_BYTES_MESSAGE = "is too long (maximum 72 bytes when encoded as UTF-8)";

    static final int MIN_CHARS = 8;
    static final int MAX_CHARS = 64;
    static final int MAX_BYTES = 72;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        int chars = value.codePointCount(0, value.length());
        boolean hasLetter = value.codePoints().anyMatch(Character::isLetter);
        boolean hasDigit = value.codePoints().anyMatch(Character::isDigit);
        if (chars < MIN_CHARS || chars > MAX_CHARS || !hasLetter || !hasDigit) {
            return reject(context, POLICY_MESSAGE);
        }
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return reject(context, TOO_MANY_BYTES_MESSAGE);
        }
        return true;
    }

    private static boolean reject(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}
