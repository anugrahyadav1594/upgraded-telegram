package com.realtimechat.common.realtime;

import java.util.Set;
import java.util.UUID;

/**
 * Marker for domain events that must be mirrored to connected clients.
 *
 * <p>Implementing this on a domain event means "after the transaction commits, send this to these
 * users". The broadcaster subscribes generically, so a module never has to know about STOMP, Redis
 * or session registries — the dependency points inward, not outward.
 *
 * <p>Events are always published <b>after commit</b>: telling a client about a message that a
 * rolled-back transaction never stored would be a correctness bug (see {@code docs/architecture.md §11}).
 */
public interface RealtimeEventSource {

    RealtimeEventType realtimeType();

    Set<UUID> realtimeRecipients();

    Object realtimePayload();

    /** Optional conversation topic destination for ephemeral broadcasts (typing). */
    default String topicDestination() {
        return null;
    }
}
