package com.realtimechat.auth.service;

import com.realtimechat.common.error.WeakPasswordException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Password policy.
 *
 * <p>Deliberately based on <b>length and blocklists</b> rather than character-class rules: forced
 * symbols and digits push people towards predictable patterns ("Password1!") and are no longer
 * recommended. The requirements are:
 * <ul>
 *   <li>12–128 characters (length is what actually resists cracking),</li>
 *   <li>not a known-breached/common password,</li>
 *   <li>not containing the username or the email local part,</li>
 *   <li>a small amount of character diversity so "aaaaaaaaaaaa" is rejected.</li>
 * </ul>
 *
 * <p>In production the common-password list would be backed by a maintained corpus (for example the
 * Have I Been Pwned k-anonymity API or a bundled top-100k list). This implementation ships a
 * representative subset so the rule is real rather than decorative.
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 128;

    private static final int MIN_DISTINCT_CHARACTERS = 6;

    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "password", "password1", "password123", "password1234", "passw0rd", "123456789012",
            "qwertyuiop12", "letmein12345", "iloveyou1234", "admin1234567", "welcome12345",
            "changeme1234", "correcthorsebatterystaple", "abcdefghijkl", "aaaaaaaaaaaa",
            "1234567890123", "qwerty123456", "football1234", "monkey123456", "dragon123456");

    /**
     * Validates a candidate password against the policy.
     *
     * @return {@code null} when acceptable, otherwise a user-facing explanation
     */
    public String violations(String password, String username, String email) {
        if (password == null || password.isBlank()) {
            return "Password is required";
        }
        if (password.length() < MIN_LENGTH) {
            return "Password must be at least " + MIN_LENGTH + " characters";
        }
        if (password.length() > MAX_LENGTH) {
            return "Password must be at most " + MAX_LENGTH + " characters";
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (COMMON_PASSWORDS.contains(lower)) {
            return "This password is too common — choose something less predictable";
        }
        if (distinctCharacters(lower) < MIN_DISTINCT_CHARACTERS) {
            return "Password must contain at least " + MIN_DISTINCT_CHARACTERS + " different characters";
        }
        if (username != null && username.length() >= 4 && lower.contains(username.toLowerCase(Locale.ROOT))) {
            return "Password must not contain your username";
        }
        if (email != null && email.contains("@")) {
            String localPart = email.substring(0, email.indexOf('@')).toLowerCase(Locale.ROOT);
            if (localPart.length() >= 4 && lower.contains(localPart)) {
                return "Password must not contain your email address";
            }
        }
        return null;
    }

    /** Throws the API-level error used by registration and password change. */
    public void validate(String password, String username, String email) {
        String violation = violations(password, username, email);
        if (violation != null) {
            throw new WeakPasswordException(violation);
        }
    }

    private int distinctCharacters(String value) {
        boolean[] seen = new boolean[128];
        int distinct = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            int index = c < 128 ? c : 127;
            if (!seen[index]) {
                seen[index] = true;
                distinct++;
            }
        }
        return distinct;
    }

    /** Convenience for tests/documentation: the list of rules actually enforced. */
    public List<String> rules() {
        return List.of(
                "At least " + MIN_LENGTH + " characters",
                "At most " + MAX_LENGTH + " characters",
                "Not a commonly used password",
                "At least " + MIN_DISTINCT_CHARACTERS + " different characters",
                "Must not contain your username or email address");
    }
}
