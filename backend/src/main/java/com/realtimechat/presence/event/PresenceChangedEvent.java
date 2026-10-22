package com.realtimechat.presence.event;

import com.realtimechat.presence.dto.PresenceStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a user's presence changes.
 *
 * <p>Deliberately <b>not</b> a {@code RealtimeEventSource}: presence has a dynamic audience
 * (participants of the user's conversations, minus blocked users) which the presence module must not
 * know about — computing it would make presence depend on conversations and create a cycle.
 * The conversations module listens for this event, resolves the audience and forwards it.
 *
 * @param userId     the user whose presence changed
 * @param status     unfiltered presence status
 * @param lastSeenAt last activity instant (null while online)
 */
public record PresenceChangedEvent(UUID userId, PresenceStatus status, Instant lastSeenAt) {
}
