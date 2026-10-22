package com.realtimechat.conversations.event;

import com.realtimechat.common.realtime.RealtimeEventSource;
import com.realtimechat.common.realtime.RealtimeEventType;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The reader advanced their read cursor; the sender's ticks must move.
 *
 * <p>Only sent when the reader has read receipts enabled — a user who turned receipts off still gets
 * their own unread badge cleared (that state is theirs), but their counterpart is not told when they
 * read. The settings are applied at publish time, not by the client, so the privacy switch cannot be
 * bypassed by a modified frontend.
 */
public record MessageReadEvent(UUID conversationId, UUID readerId, UUID recipientId,
                               UUID lastMessageId, Instant readAt) implements RealtimeEventSource {

    @Override
    public RealtimeEventType realtimeType() {
        return RealtimeEventType.MESSAGE_READ_UPDATED;
    }

    @Override
    public Set<UUID> realtimeRecipients() {
        return Set.of(recipientId);
    }

    @Override
    public Object realtimePayload() {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("conversationId", conversationId);
        payload.put("readerId", readerId);
        // A watermark, not a list: a user who returns after a week may read thousands of messages, and
        // the frame must stay small regardless. Clients mark everything at or before this message read.
        payload.put("lastMessageId", lastMessageId);
        payload.put("readAt", readAt);
        return payload;
    }
}
