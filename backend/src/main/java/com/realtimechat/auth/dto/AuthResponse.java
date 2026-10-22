package com.realtimechat.auth.dto;

import com.realtimechat.users.dto.AccountResponse;

import java.time.Instant;

/**
 * Response of register/login.
 *
 * <p>Contains no token: the session lives in an {@code HttpOnly} cookie, so there is nothing for the
 * frontend to store and nothing for an XSS payload to read. {@code session.expiresAt} is exposed only
 * so the UI can warn before an idle timeout.
 */
public record AuthResponse(AccountResponse user, SessionInfo session) {

    public static AuthResponse of(AccountResponse user, Instant expiresAt) {
        return new AuthResponse(user, new SessionInfo(expiresAt));
    }

    /** Absolute expiry of the session created by this request. */
    public record SessionInfo(Instant expiresAt) {
    }
}
