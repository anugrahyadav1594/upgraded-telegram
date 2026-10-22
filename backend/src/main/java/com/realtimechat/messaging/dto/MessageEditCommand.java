package com.realtimechat.messaging.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** STOMP {@code /app/message.edit}. The author is taken from the socket principal. */
public record MessageEditCommand(
        @NotNull UUID messageId,
        @NotNull String content,
        UUID clientMessageId
) {
}
