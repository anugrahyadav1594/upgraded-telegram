package com.realtimechat.common.pagination;

import java.time.Instant;
import java.util.UUID;

/**
 * Decoded pagination cursor: a sort key plus a tiebreaker id.
 *
 * <p>Keyset pagination instead of {@code OFFSET}: it stays correct while rows are inserted (chat is
 * append-heavy) and can always be answered from an index. The sort key is opaque to clients and is
 * typed here — an instant for time-ordered resources, a string (e.g. username) for name-ordered
 * ones — so one codec serves every list endpoint.
 */
public record Cursor(String sortKey, UUID id) {

    public static Cursor ofInstant(Instant sortKey, UUID id) {
        return new Cursor(Long.toString(sortKey.toEpochMilli()), id);
    }

    public static Cursor ofString(String sortKey, UUID id) {
        return new Cursor(sortKey, id);
    }

    /** Interprets the key as an instant; throws when the cursor belongs to a different ordering. */
    public Instant instantKey() {
        return Instant.ofEpochMilli(Long.parseLong(sortKey));
    }
}
