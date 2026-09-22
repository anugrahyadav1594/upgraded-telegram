package com.realtimechat.common.realtime;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Internal transport envelope published to Redis.
 *
 * <p>Distinct from what the client receives: it additionally carries the recipient set (used by
 * each instance to decide what to push) while {@code payload} stays as raw JSON so the fan-out path
 * never re-serialises per recipient.
 */
public record RealtimeEnvelope(
        UUID eventId,
        String type,
        Set<UUID> recipients,
        String topicDestination,
        String payloadJson,
        Instant occurredAt
) {
}
