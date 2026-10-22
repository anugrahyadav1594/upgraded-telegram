package com.realtimechat.messaging.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * STOMP {@code /app/chat.send} payload.
 *
 * <p>Note what is missing: no {@code senderId}, no {@code conversationSequence}, no {@code status}.
 * Everything that determines authorisation or ordering is derived server-side from the session and the
 * database — a client can send those fields, but they are simply not bound (unknown properties are
 * rejected, so a client that tries gets a clear error instead of silent misbehaviour).
 */
public record ChatSendCommand(
        @NotNull UUID conversationId,
        @NotNull String content,
        @NotNull UUID clientMessageId,
        String messageType,
        UUID replyToMessageId
) {
}
