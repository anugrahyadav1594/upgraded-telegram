package com.realtimechat.messaging.dto;

import com.realtimechat.common.validation.ValidMessageContent;
import jakarta.validation.constraints.NotNull;

/** {@code PATCH /api/v1/messages/{messageId}} body. */
public record EditMessageRequest(
        @NotNull(message = "content is required")
        @ValidMessageContent
        String content
) {
}
