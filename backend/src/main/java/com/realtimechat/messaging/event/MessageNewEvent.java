package com.realtimechat.messaging.event;

import com.realtimechat.common.realtime.RealtimeEventSource;
import com.realtimechat.common.realtime.RealtimeEventType;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A new message reached the recipients. One payload per recipient (their receipt differs). */
public record MessageNewEvent(Set<UUID> recipientIds, Object message) implements RealtimeEventSource {

    @Override
    public RealtimeEventType realtimeType() {
        return RealtimeEventType.MESSAGE_NEW;
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
