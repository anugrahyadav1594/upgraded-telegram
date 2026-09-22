package com.realtimechat.moderation.repository;

import com.realtimechat.moderation.entity.ReportStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Append-only audit trail lookups. */
public interface ReportStatusHistoryRepository extends JpaRepository<ReportStatusHistory, UUID> {

    List<ReportStatusHistory> findByReportIdOrderByCreatedAtAsc(UUID reportId);
}
