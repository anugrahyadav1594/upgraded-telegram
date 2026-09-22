package com.realtimechat.common.validation;

import com.realtimechat.common.util.TextNormalizer;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates after normalisation, so {@code "@Ghost"} is judged as {@code ghost} (the value that
 * would actually be stored) rather than rejected for its original casing.
 */
public class ValidUsernameValidator implements ConstraintValidator<ValidUsername, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        String normalized = TextNormalizer.normalizeUsername(value);
        return UsernameRules.isValid(normalized);
    }
}
