package com.realtimechat.presence.dto;

/** Presence states as seen by other users. */
public enum PresenceStatus {
    /** At least one active connection. */
    ONLINE,
    /** Connected but idle beyond the configured threshold (derived, not server-enforced). */
    AWAY,
    /** No active connection. */
    OFFLINE,
    /**
     * The user has hidden their presence. Emitted instead of ONLINE/OFFLINE so a client that already
     * saw a green dot clears it, while revealing nothing about activity to a user who never knew.
     */
    HIDDEN
}
