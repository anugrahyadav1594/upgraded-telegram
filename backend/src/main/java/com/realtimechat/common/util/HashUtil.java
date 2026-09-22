package com.realtimechat.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Hashing and token generation helpers.
 *
 * <p>Usage rules that the rest of the codebase follows:
 * <ul>
 *   <li><b>Passwords</b> are never hashed here — they go through Argon2id
 *       ({@code PasswordEncoder}). This class is for <em>high-entropy</em> values only
 *       (session tokens, reset tokens) where a fast hash is correct.</li>
 *   <li>Tokens are generated from {@link SecureRandom} with 256 bits of entropy and stored only as
 *       their SHA-256 hash, so a database dump cannot be replayed as a login.</li>
 *   <li>{@link java.util.Random} is never used for anything security-relevant.</li>
 * </ul>
 */
public final class HashUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32; // 256 bits

    private HashUtil() {
    }

    /** A URL-safe, 256-bit random token (session token, password-reset token). */
    public static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 hex digest of a UTF-8 string. */
    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable", ex);
        }
    }

    /** Constant-time comparison, used whenever two secrets are compared. */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
