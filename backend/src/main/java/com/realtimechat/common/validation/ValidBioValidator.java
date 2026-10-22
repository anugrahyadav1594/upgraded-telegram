package com.realtimechat.common.validation;

import com.realtimechat.common.util.TextNormalizer;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidBioValidator implements ConstraintValidator<ValidBio, String> {

    private static final int MAX_LENGTH = 280;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true; // optional
        }
        String normalized = TextNormalizer.normalizeText(value);
        if (normalized == null || normalized.length() > MAX_LENGTH) {
            return false;
        }
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (Character.isISOControl(c) && c != '\n' && c != '\t') {
                return false;
            }
        }
        return true;
    }
}
