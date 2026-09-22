package com.realtimechat.messaging.event;

import com.realtimechat.common.realtime.RealtimeEventSource;
import com.realtimechat.common.realtime.RealtimeEventType;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A message became a tombstone.
 *
 * <p>The payload deliberately carries <em>no content</em> — not even the previous text. A client that
 * wants to display "you deleted your own message" already knows the content it rendered before; every
 * other client has no business receiving the erased text a second time.
 */
public record MessageDeletedEvent(Set<UUID> recipientIds, UUID messageId, UUID conversationId, Instant deletedAt)
        implements RealtimeEventSource {

    @Override
    public RealtimeEventType realtimeType() {
        return RealtimeEventType.MESSAGE_DELETED;
    }

    @Override
    public Set<UUID> realtimeRecipients() {
        return recipientIds;
    }

    @Override
    public Object realtimePayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageId", messageId);
        payload.put("conversationId", conversationId);
        payload.put("deletedAt", deletedAt);
        return payload;
    }
}
