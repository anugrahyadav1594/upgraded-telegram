package com.realtimechat.messaging.dto;

import java.util.UUID;

/**
 * The quoted parent of a reply.
 *
 * <p>Only a short preview is embedded, not the whole message: a reply chain of arbitrary depth would
 * otherwise expand into an unbounded payload. A deleted parent keeps its id and renders as a
 * tombstone, so the reply's context is never silently lost.
 */
public record ReplyPreviewResponse(UUID id, UUID senderId, String preview, boolean deleted) {
}
