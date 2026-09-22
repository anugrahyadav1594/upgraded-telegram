package com.realtimechat.moderation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit entry for a moderation decision.
 *
 * <p>Written for every transition, including the first (from {@code null} to {@code OPEN}): a
 * moderator asking "what happened to this report?" should never have to infer history from the current
 * status alone.
 */
@Entity
@Table(name = "report_status_history")
public class ReportStatusHistory {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "report_id", nullable = false, updatable = false)
    private UUID reportId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 16)
    private ReportStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 16)
    private ReportStatus toStatus;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "note", length = 1000)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ReportStatusHistory() {
        // for JPA
    }

    public static ReportStatusHistory of(UUID reportId, ReportStatus from, ReportStatus to, UUID actorId,
                                         String note) {
        ReportStatusHistory history = new ReportStatusHistory();
        history.id = UUID.randomUUID();
        history.reportId = reportId;
        history.fromStatus = from;
        history.toStatus = to;
        history.actorId = actorId;
        history.note = note;
        return history;
    }

    public UUID getId() {
        return id;
    }

    public UUID getReportId() {
        return reportId;
    }

    public ReportStatus getFromStatus() {
        return fromStatus;
    }

    public ReportStatus getToStatus() {
        return toStatus;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
