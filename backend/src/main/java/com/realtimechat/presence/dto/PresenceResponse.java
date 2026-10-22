package com.realtimechat.presence.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code GET /api/v1/users/{userId}/presence} response.
 *
 * @param visible false when the owner hides presence; {@code status} and {@code lastSeenAt} are then
 *                null and the client renders nothing at all (never "hidden", which would leak that
 *                a setting exists and was changed)
 */
public record PresenceResponse(UUID userId, String status, Instant lastSeenAt, boolean visible) {
}
