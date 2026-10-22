package com.realtimechat.common.validation;

import com.realtimechat.common.util.TextNormalizer;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidDisplayNameValidator implements ConstraintValidator<ValidDisplayName, String> {

    private static final int MAX_LENGTH = 64;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        String normalized = TextNormalizer.normalizeDisplayName(value);
        return normalized != null && !normalized.isBlank() && normalized.length() <= MAX_LENGTH;
    }
}
