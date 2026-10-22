package com.realtimechat.profiles.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code GET /api/v1/users/{username}} response.
 *
 * <p>Every field here is safe to show to any authenticated user. Presence fields are {@code null}
 * when the profile owner's privacy settings hide them — the client renders nothing rather than
 * "hidden", so a hidden user is indistinguishable from a user who never appeared online.
 */
public record PublicProfileResponse(
        UUID id,
        String username,
        boolean usernamePublic,
        String displayName,
        String avatarUrl,
        String bio,
        Boolean online,
        Instant lastSeenAt,
        Instant memberSince,
        String existingConversationId,
        boolean blockedByMe,
        boolean canMessage
) {
}
