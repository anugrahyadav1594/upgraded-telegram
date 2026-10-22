package com.realtimechat.common.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Normalisation rules shared by the whole application.
 *
 * <p>Keeping this in one place is what makes "case-insensitive, whitespace-tolerant, Unicode-safe"
 * behaviour consistent between the database constraints, the service layer and the API contract.
 */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    /**
     * Email normalisation: trim + lowercase. Deliberately does <em>not</em> strip dots or plus
     * tags: those are provider-specific, and guessing them breaks legitimate addresses.
     */
    public static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Username normalisation: trim, strip a leading {@code @}, lowercase, NFC. */
    public static String normalizeUsername(String username) {
        if (username == null) {
            return null;
        }
        String value = username.trim();
        if (value.startsWith("@")) {
            value = value.substring(1);
        }
        return Normalizer.normalize(value, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }

    /** Display names keep their case and inner spacing but lose control characters and padding. */
    public static String normalizeDisplayName(String displayName) {
        if (displayName == null) {
            return null;
        }
        return stripControlCharacters(displayName).trim().replaceAll("\\s{2,}", " ");
    }

    /** Free text (bio, message content): normalise newlines, drop control characters except \n \t. */
    public static String normalizeText(String text) {
        if (text == null) {
            return null;
        }
        return text.replace("\r\n", "\n").replace('\r', '\n').strip();
    }

    /** Removes characters that have no business being in stored text (zero-width, control). */
    public static String stripControlCharacters(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean allowedWhitespace = c == '\n' || c == '\t';
            boolean control = Character.isISOControl(c);
            boolean zeroWidth = c == '\u200B' || c == '\u200C' || c == '\u200D' || c == '\uFEFF';
            if ((!control && !zeroWidth) || allowedWhitespace) {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    /** True when the text contains only whitespace. */
    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
