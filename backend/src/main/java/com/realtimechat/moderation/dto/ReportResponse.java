package com.realtimechat.moderation.dto;

import com.realtimechat.moderation.entity.Report;

import java.time.Instant;
import java.util.UUID;

/**
 * A report as the <b>reporter</b> sees it.
 *
 * <p>Deliberately excludes the moderator's note and identity: what happened to a report is the
 * reporter's business, but which moderator handled it and what internal notes were written are not.
 */
public record ReportResponse(
        UUID id,
        String targetType,
        UUID targetUserId,
        UUID targetMessageId,
        String reason,
        String description,
        String status,
        Instant createdAt,
        Instant updatedAt
) {

    public static ReportResponse from(Report report) {
        return new ReportResponse(report.getId(), report.getTargetType().name(), report.getTargetUserId(),
                report.getTargetMessageId(), report.getReason().name(), report.getDescription(),
                report.getStatus().name(), report.getCreatedAt(), report.getUpdatedAt());
    }
}
