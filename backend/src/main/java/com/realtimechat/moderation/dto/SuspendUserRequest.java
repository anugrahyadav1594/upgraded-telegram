package com.realtimechat.moderation.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * {@code POST /api/v1/admin/users/{userId}/suspend} body.
 *
 * <p>{@code until} is optional: omitting it means "indefinite, pending review" — an intentionally
 * explicit choice rather than an arbitrary far-future date, so a permanent ban is always a human
 * decision recorded in the audit trail.
 */
public record SuspendUserRequest(
        @Size(max = 500, message = "Reason is too long")
        String reason,

        Instant until,

        @Pattern(regexp = "SUSPEND|REINSTATE", message = "Unknown action")
        String action
) {
}
