package com.realtimechat.moderation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code PATCH /api/v1/admin/reports/{id}} body: the moderator's decision. */
public record UpdateReportStatusRequest(
        @NotBlank(message = "status is required")
        @Pattern(regexp = "REVIEWING|RESOLVED|DISMISSED", message = "status must be REVIEWING, RESOLVED or DISMISSED")
        String status,

        @Size(max = 1000, message = "Note must be at most 1000 characters")
        String note
) {
}
