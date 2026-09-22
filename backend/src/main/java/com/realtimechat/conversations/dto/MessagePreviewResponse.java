package com.realtimechat.conversations.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One-line summary of a conversation's newest message, as shown in the list.
 *
 * <p>Carries a preview rather than the full message because the list does not need the body: keeping
 * message content out of the list payload reduces both the response size and the amount of user
 * content that can leak into a cache or a log.
 */
public record MessagePreviewResponse(
        UUID id,
        UUID senderId,
        String preview,
        String messageType,
        Instant createdAt,
        boolean deleted
) {
}
