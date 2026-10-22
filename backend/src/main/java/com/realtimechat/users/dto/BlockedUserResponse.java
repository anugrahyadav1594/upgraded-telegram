package com.realtimechat.users.dto;

import java.time.Instant;
import java.util.UUID;

/** One entry of {@code GET /api/v1/blocks}. Contains public profile data only. */
public record BlockedUserResponse(
        UUID userId,
        String username,
        String displayName,
        String avatarUrl,
        Instant blockedAt
) {
}
