package com.realtimechat.messaging.event;

import com.realtimechat.common.realtime.RealtimeEventSource;
import com.realtimechat.common.realtime.RealtimeEventType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A reaction was toggled. The aggregated list is included so a client can replace its state instead of
 * recomputing it, and so a missed frame cannot leave a stale count on screen.
 */
public record MessageReactionUpdatedEvent(Set<UUID> recipientIds, UUID messageId, UUID conversationId,
                                          Object reactions) implements RealtimeEventSource {

    @Override
    public RealtimeEventType realtimeType() {
        return RealtimeEventType.MESSAGE_REACTION_UPDATED;
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
        payload.put("reactions", reactions);
        return payload;
    }
}
