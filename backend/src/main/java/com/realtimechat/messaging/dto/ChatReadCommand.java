package com.realtimechat.messaging.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** STOMP {@code /app/chat.read}: "I have seen everything up to this message". */
public record ChatReadCommand(
        @NotNull UUID conversationId,
        @NotNull UUID lastMessageId
) {
}
