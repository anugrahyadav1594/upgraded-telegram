package com.realtimechat.auth.dto;

import com.realtimechat.auth.entity.UserSession;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of {@code GET /api/v1/auth/sessions}.
 *
 * <p>Exposes the session id (so it can be revoked) but never the token or its hash. The user agent
 * and IP are shown to the account owner only — they are what makes "is this a session I recognise?"
 * answerable.
 */
public record SessionResponse(
        UUID id,
        boolean current,
        String userAgent,
        String ipAddress,
        Instant createdAt,
        Instant lastUsedAt,
        Instant expiresAt
) {

    public static SessionResponse from(UserSession session, UUID currentSessionId) {
        return new SessionResponse(
                session.getId(),
                session.getId().equals(currentSessionId),
                session.getUserAgent(),
                session.getIpAddress() == null ? null : session.getIpAddress().getHostAddress(),
                session.getCreatedAt(),
                session.getLastUsedAt(),
                session.getExpiresAt());
    }
}
