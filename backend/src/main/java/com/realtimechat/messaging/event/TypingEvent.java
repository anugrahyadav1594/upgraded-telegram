package com.realtimechat.messaging.event;

import com.realtimechat.common.realtime.RealtimeEventSource;
import com.realtimechat.common.realtime.RealtimeEventType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ephemeral typing indicator.
 *
 * <p>Delivered both to the counterpart's user queue (so it works without any per-conversation
 * subscription) and to the conversation topic (so a client that only has the conversation open can
 * receive it without a second channel). Duplicate delivery in that case is harmless: typing state is
 * idempotent by nature, and the frame carries an expiry so a client can time it out on its own.
 */
public record TypingEvent(UUID conversationId, UUID userId, String displayName, boolean typing,
                          UUID recipientId, long expiresInMs) implements RealtimeEventSource {

    @Override
    public RealtimeEventType realtimeType() {
        return typing ? RealtimeEventType.TYPING_STARTED : RealtimeEventType.TYPING_STOPPED;
    }

    @Override
    public Set<UUID> realtimeRecipients() {
        return Set.of(recipientId);
    }

    @Override
    public String topicDestination() {
        return "/topic/conversation/" + conversationId;
    }

    @Override
    public Object realtimePayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversationId", conversationId);
        payload.put("userId", userId);
        payload.put("displayName", displayName);
        payload.put("expiresInMs", expiresInMs);
        return payload;
    }
}
