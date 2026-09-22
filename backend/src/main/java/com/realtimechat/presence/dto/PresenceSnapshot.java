package com.realtimechat.presence.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Point-in-time presence of one user, already filtered by privacy for a specific requester.
 *
 * @param userId     the subject
 * @param status     filtered status ({@code HIDDEN} when the owner hides presence)
 * @param lastSeenAt coarse last activity, only present when the owner allows it and is offline
 */
public record PresenceSnapshot(UUID userId, PresenceStatus status, Instant lastSeenAt) {

    public boolean isOnline() {
        return status == PresenceStatus.ONLINE || status == PresenceStatus.AWAY;
    }

    public static PresenceSnapshot hidden(UUID userId) {
        return new PresenceSnapshot(userId, PresenceStatus.HIDDEN, null);
    }
}
