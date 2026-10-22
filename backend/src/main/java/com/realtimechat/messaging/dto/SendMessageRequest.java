package com.realtimechat.messaging.dto;

import com.realtimechat.common.validation.ValidMessageContent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * {@code POST /api/v1/conversations/{id}/messages} body.
 *
 * <p>{@code clientMessageId} is required, not optional: without an idempotency key a client that loses
 * the response (mobile network switch, tab suspended) can only choose between losing the message and
 * duplicating it. With one, the retry is free.
 */
public record SendMessageRequest(
        @NotNull(message = "content is required")
        @ValidMessageContent
        String content,

        @NotNull(message = "clientMessageId is required")
        UUID clientMessageId,

        @Size(max = 16, message = "Unknown message type")
        String messageType,

        UUID replyToMessageId
) {
}
