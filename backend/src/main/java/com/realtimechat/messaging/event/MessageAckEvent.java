package com.realtimechat.messaging.event;

import com.realtimechat.common.realtime.RealtimeEventSource;
import com.realtimechat.common.realtime.RealtimeEventType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The sender's own message is committed; the optimistic bubble can be reconciled.
 *
 * <p>Sent to the sender's sessions only (not the conversation topic) because it carries the
 * {@code clientMessageId} binding: it is a private answer to a private request, and broadcasting it to
 * the peer would tell them nothing useful while leaking the idempotency key.
 *
 * <p>This is the frame that makes the delivery guarantee explicit — it is emitted <b>after</b> the
 * database transaction commits, so "acknowledged" always means "durable".
 */
public record MessageAckEvent(UUID senderId, UUID clientMessageId, Object message) implements RealtimeEventSource {

    @Override
    public RealtimeEventType realtimeType() {
        return RealtimeEventType.MESSAGE_ACK;
    }

    @Override
    public Set<UUID> realtimeRecipients() {
        return Set.of(senderId);
    }

    @Override
    public Object realtimePayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("clientMessageId", clientMessageId);
        payload.put("message", message);
        return payload;
    }
}
