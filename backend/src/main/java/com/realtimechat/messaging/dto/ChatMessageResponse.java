package com.realtimechat.messaging.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A message, in exactly the shape REST and WebSocket both use.
 *
 * <p>One representation for both transports is a deliberate contract decision (see
 * {@code docs/websocket-protocol.md §2.3}): the client has a single renderer, a single cache entry
 * type and a single set of tests. A "slightly different" socket payload is how the two paths drift.
 *
 * @param content null for a tombstoned message — the content is erased server-side, so a deleted
 *                message cannot be recovered by inspecting the response
 * @param sequence server-assigned order token, used by clients as a resync watermark
 */
public record ChatMessageResponse(
        UUID id,
        UUID conversationId,
        UUID senderId,
        String content,
        String messageType,
        UUID clientMessageId,
        Long sequence,
        ReplyPreviewResponse replyTo,
        List<ReactionSummaryResponse> reactions,
        ReceiptResponse receipt,
        Instant editedAt,
        Instant deletedAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static ChatMessageResponse tombstone(UUID id, UUID conversationId, UUID senderId, Instant deletedAt) {
        return new ChatMessageResponse(id, conversationId, senderId, null, "TEXT", null, null, null,
                List.of(), ReceiptResponse.sent(), null, deletedAt, deletedAt, deletedAt);
    }
}
