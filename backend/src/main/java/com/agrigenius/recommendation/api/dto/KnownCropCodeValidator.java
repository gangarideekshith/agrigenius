package com.agrigenius.recommendation.api.dto;

import com.agrigenius.recommendation.infrastructure.CropRepository;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Spring-managed validator (the repository is injected by Spring's validator factory). */
public class KnownCropCodeValidator implements ConstraintValidator<KnownCropCode, String> {

    private final CropRepository crops;

    public KnownCropCodeValidator(CropRepository crops) {
        this.crops = crops;
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || crops.existsById(value);
    }
}
