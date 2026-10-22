package com.realtimechat.conversations.event;

import com.realtimechat.common.realtime.RealtimeEventSource;
import com.realtimechat.common.realtime.RealtimeEventType;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Someone started a conversation with you.
 *
 * <p>Sent to the <em>other</em> participant only: the creator already has the conversation in the
 * response to their request, and echoing it back would make the client reconcile a duplicate.
 */
public record ConversationCreatedEvent(UUID initiatorId, UUID recipientId, Map<String, Object> conversation)
        implements RealtimeEventSource {

    @Override
    public RealtimeEventType realtimeType() {
        return RealtimeEventType.CONVERSATION_CREATED;
    }

    @Override
    public Set<UUID> realtimeRecipients() {
        return Set.of(recipientId);
    }

    @Override
    public Object realtimePayload() {
        return Map.of("conversation", conversation);
    }
}
