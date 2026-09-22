package com.realtimechat.messaging.event;

import java.util.Set;
import java.util.UUID;

/**
 * A message became durable — the notification module's trigger.
 *
 * <p>Deliberately a plain application event rather than the realtime frame: notifications need the
 * conversation, the message, the author and a short preview, and coupling them to the WebSocket
 * payload shape would mean a change to the wire format silently changing what notifications contain.
 * The two concerns share the commit moment, nothing else.
 *
 * <p>Published inside the transaction, consumed with {@code AFTER_COMMIT} semantics, so a rolled-back
 * send produces no notification.
 */
public record MessageCommittedEvent(
        UUID conversationId,
        UUID messageId,
        UUID senderId,
        Set<UUID> recipientIds,
        String preview
) {
}
