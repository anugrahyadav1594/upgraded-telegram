package com.realtimechat.conversations.service;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Read-side port the conversations module needs from the messaging module.
 *
 * <p>Why a port in this direction: {@code messaging} already depends on {@code conversations} for
 * membership checks ("may this user send here?"). If conversations also imported messaging entities,
 * the two modules would be mutually dependent and neither could be extracted or tested alone. The
 * port states exactly what conversations needs — a preview line and an unread count — and keeps the
 * dependency one-way.
 */
public interface MessagePreviewPort {

    /** Last-message previews for the given message ids (missing ids are simply absent). */
    Map<UUID, MessagePreview> previewsOf(Collection<UUID> messageIds);

    /** Unread counts per conversation for one user, derived from server-authoritative receipts. */
    Map<UUID, Integer> unreadCounts(UUID userId, Collection<UUID> conversationIds);

    /** Total unread across all conversations (drives the global badge). */
    int totalUnread(UUID userId);

    /**
     * @param preview  first line of the content, already truncated for list rendering
     * @param deleted  tombstoned messages render as "Message deleted" without revealing the content
     */
    record MessagePreview(UUID messageId, UUID senderId, String preview, String messageType,
                          Instant createdAt, boolean deleted, boolean edited) {
    }
}
