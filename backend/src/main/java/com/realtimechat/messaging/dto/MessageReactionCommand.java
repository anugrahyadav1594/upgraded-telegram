package com.realtimechat.messaging.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** STOMP {@code /app/message.reaction}: toggle an emoji reaction. */
public record MessageReactionCommand(
        @NotNull UUID messageId,
        @NotNull String emoji,
        UUID clientMessageId
) {
}
