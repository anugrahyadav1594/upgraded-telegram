package com.realtimechat.users.entity;

/** Account lifecycle state. Mirrors the {@code users.status} CHECK constraint. */
public enum UserStatus {
    /** Normal, usable account. */
    ACTIVE,
    /** Suspended by moderation: cannot sign in, existing content is retained. */
    SUSPENDED,
    /** Tombstone for a deleted account: content is anonymised, the row keeps referential integrity. */
    DELETED;

    public boolean canAuthenticate() {
        return this == ACTIVE;
    }
}
