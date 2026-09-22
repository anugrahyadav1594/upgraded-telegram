package com.realtimechat.messaging.event;

import com.realtimechat.common.realtime.RealtimeEventSource;
import com.realtimechat.common.realtime.RealtimeEventType;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * "Reached the device" — the middle tick, and the one that is easiest to get wrong.
 *
 * <p>It is emitted only after the recipient's own client acknowledged, never when the server merely
 * published a frame: a message sitting in an offline user's queue has not been delivered to anything.
 */
public record MessageDeliveredEvent(UUID senderId, UUID recipientId, UUID conversationId,
                                    UUID lastMessageId, Instant deliveredAt) implements RealtimeEventSource {

    @Override
    public RealtimeEventType realtimeType() {
        return RealtimeEventType.MESSAGE_DELIVERED;
    }

    @Override
    public Set<UUID> realtimeRecipients() {
        return Set.of(senderId);
    }

    @Override
    public Object realtimePayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversationId", conversationId);
        payload.put("recipientId", recipientId);
        payload.put("lastMessageId", lastMessageId);
        payload.put("deliveredAt", deliveredAt);
        return payload;
    }
}
