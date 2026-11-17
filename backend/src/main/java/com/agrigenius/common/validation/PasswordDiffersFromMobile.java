package com.agrigenius.common.validation;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Rejects a password equal to the mobile number (raw, normalised or 10-digit form).
 * The violation is reported on the {@code password} property. Use on types implementing {@link MobileAndPassword}.
 */
@Documented
@Constraint(validatedBy = PasswordDiffersFromMobileValidator.class)
@Target(TYPE)
@Retention(RUNTIME)
public @interface PasswordDiffersFromMobile {

    String message() default "must not be the same as the mobile number";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
