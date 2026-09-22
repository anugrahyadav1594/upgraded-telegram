package com.realtimechat.conversations.event;

import com.realtimechat.common.realtime.RealtimeEventSource;
import com.realtimechat.common.realtime.RealtimeEventType;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A conversation's per-user state changed (mute, archive, delete, or a new last message).
 *
 * <p>Carries only the changed fields so the client can merge instead of refetching the whole list on
 * every keystroke of a conversation partner.
 */
public record ConversationUpdatedEvent(UUID conversationId, Set<UUID> recipients, Map<String, Object> changes)
        implements RealtimeEventSource {

    @Override
    public RealtimeEventType realtimeType() {
        return RealtimeEventType.CONVERSATION_UPDATED;
    }

    @Override
    public Set<UUID> realtimeRecipients() {
        return recipients;
    }

    @Override
    public Object realtimePayload() {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("conversationId", conversationId);
        payload.putAll(changes);
        return payload;
    }
}
