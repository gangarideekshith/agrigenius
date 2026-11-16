package com.agrigenius.common.validation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/** A 10-digit Indian mobile number, optionally prefixed with +91; spaces and hyphens allowed. Null is valid. */
@Documented
@Constraint(validatedBy = IndianMobileNumberValidator.class)
@Target({FIELD, PARAMETER, TYPE_USE, RECORD_COMPONENT})
@Retention(RUNTIME)
public @interface IndianMobileNumber {

    String message() default "must be a valid 10-digit Indian mobile number";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
