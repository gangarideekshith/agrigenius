package com.agrigenius.common.validation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Password policy: 8-64 characters, at most 72 UTF-8 bytes (BCrypt ignores anything longer),
 * at least one letter and one digit. Null is valid (combine with {@code @NotBlank}).
 */
@Documented
@Constraint(validatedBy = ValidPasswordValidator.class)
@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@Retention(RUNTIME)
public @interface ValidPassword {

    String message() default "must be 8-64 characters with at least one letter and one digit";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
