package com.realtimechat.messaging.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** STOMP {@code /app/message.delete}. */
public record MessageDeleteCommand(
        @NotNull UUID messageId,
        UUID clientMessageId
) {
}
