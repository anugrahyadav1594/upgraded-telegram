package com.realtimechat.common.validation;

import com.realtimechat.common.util.TextNormalizer;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidMessageContentValidator implements ConstraintValidator<ValidMessageContent, String> {

    /** Mirrors messages_content_length in V4__messaging.sql. */
    private static final int MAX_LENGTH = 4000;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return false;
        }
        String normalized = TextNormalizer.normalizeText(value);
        if (normalized == null || normalized.isBlank()) {
            return false;
        }
        if (normalized.length() > MAX_LENGTH) {
            return false;
        }
        // Reject remaining control characters (escape, bell, null byte, …) which have no place in
        // chat content and are a classic vector for terminal/log injection.
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (Character.isISOControl(c) && c != '\n' && c != '\t') {
                return false;
            }
        }
        return true;
    }
}
