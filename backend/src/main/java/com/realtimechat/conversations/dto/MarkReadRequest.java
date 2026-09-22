package com.realtimechat.conversations.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Body of the read endpoints: the newest message the caller has seen. */
public record MarkReadRequest(
        @NotNull(message = "lastMessageId is required")
        UUID lastMessageId
) {
}
