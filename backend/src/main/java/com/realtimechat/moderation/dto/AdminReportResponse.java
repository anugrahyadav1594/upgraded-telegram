package com.realtimechat.moderation.dto;

import com.realtimechat.moderation.entity.Report;

import java.time.Instant;
import java.util.UUID;

/**
 * A report as a <b>moderator</b> sees it, including the reporter (needed to detect report-spam
 * campaigns) and the decision fields.
 */
public record AdminReportResponse(
        UUID id,
        UUID reporterId,
        String targetType,
        UUID targetUserId,
        UUID targetMessageId,
        String reason,
        String description,
        String status,
        String resolutionNote,
        UUID reviewedBy,
        Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static AdminReportResponse from(Report report) {
        return new AdminReportResponse(report.getId(), report.getReporterId(), report.getTargetType().name(),
                report.getTargetUserId(), report.getTargetMessageId(), report.getReason().name(),
                report.getDescription(), report.getStatus().name(), report.getResolutionNote(),
                report.getReviewedBy(), report.getReviewedAt(), report.getCreatedAt(), report.getUpdatedAt());
    }
}
