package com.agrigenius.recommendation.api.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** The value must be a crop code in the {@code crops} table. {@code null} is valid. */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = KnownCropCodeValidator.class)
public @interface KnownCropCode {

    String message() default "must be a known crop code";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
