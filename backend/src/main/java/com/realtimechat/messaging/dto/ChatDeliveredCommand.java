package com.realtimechat.messaging.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * STOMP {@code /app/chat.delivered}: "these messages reached my device".
 *
 * <p>A watermark ({@code lastMessageId}) rather than a list: it is bounded regardless of how long the
 * client was offline, and it is monotonic, so a delayed frame cannot move delivery backwards.
 */
public record ChatDeliveredCommand(
        @NotNull UUID conversationId,
        @NotNull UUID lastMessageId
) {
}
