package com.realtimechat.moderation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * {@code POST /api/v1/reports} body.
 *
 * <p>Both target fields are optional at the bean-validation level because their requirement is
 * conditional on {@code targetType} — "a MESSAGE report needs a message id" is a cross-field rule,
 * enforced in the service where it can produce a precise error instead of an annotation puzzle.
 */
public record CreateReportRequest(
        @NotBlank(message = "targetType is required")
        @Pattern(regexp = "USER|MESSAGE", message = "targetType must be USER or MESSAGE")
        String targetType,

        UUID targetUserId,

        UUID targetMessageId,

        @NotBlank(message = "reason is required")
        @Pattern(regexp = "SPAM|HARASSMENT|HATE_SPEECH|IMPERSONATION|SEXUAL_CONTENT|VIOLENCE|SELF_HARM|OTHER",
                message = "Unknown reason")
        String reason,

        @Size(max = 1000, message = "Description must be at most 1000 characters")
        String description
) {
}
