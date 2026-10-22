package com.realtimechat.common.realtime;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * One fan-out unit: what to send, to whom, and (optionally) to which conversation topic.
 *
 * <p>Frames are addressed to <b>user ids</b>, resolved from the authenticated session — never to
 * client-supplied destinations. A {@code topicDestination} is only used for ephemeral
 * conversation-scoped signals (typing), where duplicate delivery to a user's own tabs is harmless
 * and cheaper than per-session routing.
 *
 * @param eventId unique id, also used by clients for idempotent application of a frame
 * @param type    the event type (wire name comes from it)
 * @param payload the JSON-serialisable body (a DTO, never an entity)
 * @param recipients user ids whose local sessions should receive this frame
 * @param topicDestination optional STOMP topic for conversation-scoped ephemeral events
 * @param occurredAt server timestamp
 */
public record RealtimeEvent(
        UUID eventId,
        RealtimeEventType type,
        Object payload,
        Set<UUID> recipients,
        String topicDestination,
        Instant occurredAt
) {

    public static RealtimeEvent toUsers(RealtimeEventType type, Object payload, Set<UUID> recipients) {
        return new RealtimeEvent(UUID.randomUUID(), type, payload, Set.copyOf(recipients), null, Instant.now());
    }

    public static RealtimeEvent toUser(RealtimeEventType type, Object payload, UUID recipient) {
        return toUsers(type, payload, Set.of(recipient));
    }

    public static RealtimeEvent toTopic(RealtimeEventType type, String destination, Object payload) {
        return new RealtimeEvent(UUID.randomUUID(), type, payload, Set.of(), destination, Instant.now());
    }
}
