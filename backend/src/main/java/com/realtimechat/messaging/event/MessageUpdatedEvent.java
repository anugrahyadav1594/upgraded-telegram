package com.realtimechat.messaging.event;

import com.realtimechat.common.realtime.RealtimeEventSource;
import com.realtimechat.common.realtime.RealtimeEventType;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** An edit was applied; every participant's copy of the message must be replaced. */
public record MessageUpdatedEvent(Set<UUID> recipientIds, Object message) implements RealtimeEventSource {

    @Override
    public RealtimeEventType realtimeType() {
        return RealtimeEventType.MESSAGE_UPDATED;
    }

    @Override
    public Set<UUID> realtimeRecipients() {
        return recipientIds;
    }

    @Override
    public Object realtimePayload() {
        return Map.of("message", message);
    }
}
