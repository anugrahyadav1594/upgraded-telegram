package com.realtimechat.conversations.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A conversation as returned by list, get, create, patch and the {@code conversation.created} event.
 *
 * <p>One shape everywhere means one client-side renderer and one test fixture; a second, subtly
 * different representation is how list and detail views end up disagreeing.
 */
public record ConversationResponse(
        UUID id,
        String type,
        ConversationPeerResponse peer,
        MessagePreviewResponse lastMessage,
        int unreadCount,
        boolean muted,
        boolean archived,
        Instant lastActivityAt,
        Instant createdAt
) {
}
