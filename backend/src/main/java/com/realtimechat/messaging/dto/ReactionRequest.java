package com.realtimechat.messaging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/messages/{messageId}/reaction} body.
 *
 * <p>The emoji is validated against a curated allow-list in the service, not merely by length: an
 * allow-list keeps the feature to what the UI can render and closes the door on abusing reactions as
 * a covert channel or a text-smuggling vector.
 */
public record ReactionRequest(
        @NotBlank(message = "emoji is required")
        @Size(max = 16, message = "emoji is too long")
        String emoji
) {
}
