package com.realtimechat.moderation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A user or message report.
 *
 * <p>Reports are <b>evidence</b>: rows are never hard-deleted, and every state change is appended to
 * {@code report_status_history}. Two consequences are deliberate:
 * <ul>
 *   <li>the reported user is never notified directly — telling somebody they were reported invites
 *       retaliation and destroys the reporter's safety;</li>
 *   <li>the reporter's identity is only visible to moderators (never to the reported user), because a
 *       report is not a social interaction.</li>
 * </ul>
 */
@Entity
@Table(name = "reports")
public class Report {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "reporter_id", nullable = false, updatable = false)
    private UUID reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 16)
    private ReportTargetType targetType;

    @Column(name = "target_user_id")
    private UUID targetUserId;

    @Column(name = "target_message_id")
    private UUID targetMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 32)
    private ReportReason reason;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ReportStatus status = ReportStatus.OPEN;

    @Column(name = "resolution_note", length = 1000)
    private String resolutionNote;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Report() {
        // for JPA
    }

    public static Report forMessage(UUID reporterId, UUID messageId, UUID targetUserId, ReportReason reason,
                                    String description) {
        Report report = new Report();
        report.id = UUID.randomUUID();
        report.reporterId = reporterId;
        report.targetType = ReportTargetType.MESSAGE;
        report.targetMessageId = messageId;
        report.targetUserId = targetUserId;
        report.reason = reason;
        report.description = description;
        return report;
    }

    public static Report forUser(UUID reporterId, UUID targetUserId, ReportReason reason, String description) {
        Report report = new Report();
        report.id = UUID.randomUUID();
        report.reporterId = reporterId;
        report.targetType = ReportTargetType.USER;
        report.targetUserId = targetUserId;
        report.reason = reason;
        report.description = description;
        return report;
    }

    /**
     * Moves the report through the workflow.
     *
     * <p>The reviewer and timestamp are set here rather than by the caller, so the schema invariant
     * ("a closed report has a reviewer") cannot be violated by forgetting a field.
     */
    public void transitionTo(ReportStatus next, UUID moderatorId, String note, Instant when) {
        this.status = next;
        this.resolutionNote = note;
        this.reviewedBy = moderatorId;
        this.reviewedAt = when;
    }

    /** Called when a moderator picks the report up (REVIEWING does not close it). */
    public void startReview(UUID moderatorId, Instant when) {
        this.status = ReportStatus.REVIEWING;
        this.reviewedBy = moderatorId;
        this.reviewedAt = when;
    }

    public UUID getId() {
        return id;
    }

    public UUID getReporterId() {
        return reporterId;
    }

    public ReportTargetType getTargetType() {
        return targetType;
    }

    public UUID getTargetUserId() {
        return targetUserId;
    }

    public UUID getTargetMessageId() {
        return targetMessageId;
    }

    public ReportReason getReason() {
        return reason;
    }

    public String getDescription() {
        return description;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
