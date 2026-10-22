package com.realtimechat.messaging.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * STOMP {@code /app/chat.typing}: ephemeral typing state.
 *
 * <p>Never persisted: it is stored in Redis with a few seconds of TTL, and a client that disconnects
 * mid-keystroke simply lets the key expire. Storing typing in PostgreSQL would turn the highest
 * frequency event in the product into write amplification for data that expires by design.
 */
public record ChatTypingCommand(
        @NotNull UUID conversationId,
        boolean isTyping
) {
}
