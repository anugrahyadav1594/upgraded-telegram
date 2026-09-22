package com.realtimechat.conversations.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * The other participant of a DIRECT conversation, as the caller may see them.
 *
 * <p>{@code online}/{@code lastSeenAt} are already filtered by the peer's privacy settings and by any
 * block relationship; the frontend renders what it is given and never decides visibility itself.
 */
public record ConversationPeerResponse(
        UUID id,
        String username,
        String displayName,
        String avatarUrl,
        boolean online,
        Instant lastSeenAt
) {
}
