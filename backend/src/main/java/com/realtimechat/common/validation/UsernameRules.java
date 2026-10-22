package com.realtimechat.common.validation;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The username policy, in one place.
 *
 * <p>The same rules exist as CHECK constraints in the database (see
 * {@code V2__identity.sql}), so an invalid handle cannot be stored even if this class is bypassed.
 * Keeping both is deliberate defence in depth: the service produces a friendly
 * {@code 400}/{@code 409}, the database guarantees the invariant.
 *
 * <p>Rules: 3–32 characters, lowercase letters, digits and underscores, must start and end with a
 * letter or digit, no consecutive underscores, not reserved, no URL or impersonation patterns.
 */
public final class UsernameRules {

    public static final int MIN_LENGTH = 3;
    public static final int MAX_LENGTH = 32;
    public static final Pattern PATTERN = Pattern.compile("^[a-z0-9](?:[a-z0-9_]{1,30})[a-z0-9]$");
    public static final Pattern CONSECUTIVE_UNDERSCORES = Pattern.compile("__");

    /** Operational names nobody may hold: they would allow impersonating the platform. */
    public static final Set<String> RESERVED = Set.of(
            "admin", "administrator", "moderator", "support", "help", "security",
            "system", "root", "official", "staff", "team", "billing", "abuse",
            "realtimechat", "realchat", "deleted_account", "deleted", "anonymous",
            "me", "you", "null", "undefined");

    /** Patterns that make a handle look like a URL, an email or an impersonation attempt. */
    private static final List<Pattern> FORBIDDEN_PATTERNS = List.of(
            Pattern.compile(".*https?.*"),
            Pattern.compile(".*www\\..*"),
            Pattern.compile(".*\\.(com|net|org|io|ru|xyz|info).*"),
            Pattern.compile(".*(official|verified|realsupport|moderator)\\d*.*"));

    /** A conservative starting set; production would swap this for a maintained list. */
    private static final Set<String> BASIC_PROFANITY = Set.of(
            "fuck", "shit", "bitch", "cunt", "asshole", "bastard", "dick", "whore", "slut", "nigger", "faggot");

    private UsernameRules() {
    }

    /** Returns {@code null} when valid, otherwise a user-facing explanation. */
    public static String validate(String normalizedUsername) {
        if (normalizedUsername == null || normalizedUsername.isBlank()) {
            return "Username is required";
        }
        if (normalizedUsername.length() < MIN_LENGTH) {
            return "Username must be at least " + MIN_LENGTH + " characters";
        }
        if (normalizedUsername.length() > MAX_LENGTH) {
            return "Username must be at most " + MAX_LENGTH + " characters";
        }
        if (!normalizedUsername.equals(normalizedUsername.toLowerCase(Locale.ROOT))) {
            return "Username must contain only lowercase letters, digits and underscores";
        }
        if (!PATTERN.matcher(normalizedUsername).matches()) {
            return "Username must contain only lowercase letters, digits and single underscores, "
                    + "and must start and end with a letter or digit";
        }
        if (CONSECUTIVE_UNDERSCORES.matcher(normalizedUsername).find()) {
            return "Username cannot contain consecutive underscores";
        }
        if (RESERVED.contains(normalizedUsername)) {
            return "This username is reserved";
        }
        for (Pattern forbidden : FORBIDDEN_PATTERNS) {
            if (forbidden.matcher(normalizedUsername).matches()) {
                return "This username is not allowed";
            }
        }
        if (containsProfanity(normalizedUsername)) {
            return "This username is not allowed";
        }
        return null;
    }

    public static boolean isValid(String normalizedUsername) {
        return validate(normalizedUsername) == null;
    }

    /** Substring check against the basic list, ignoring underscores and digits used to evade it. */
    public static boolean containsProfanity(String value) {
        if (value == null) {
            return false;
        }
        String flattened = value.toLowerCase(Locale.ROOT).replace("_", "").replaceAll("\\d", "");
        for (String word : BASIC_PROFANITY) {
            if (flattened.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
