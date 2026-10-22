package com.realtimechat.moderation.repository;

import com.realtimechat.moderation.entity.Report;
import com.realtimechat.moderation.entity.ReportStatus;
import com.realtimechat.moderation.entity.ReportTargetType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Report persistence for both sides: the reporter's own submissions and the moderator queue.
 *
 * <p>The "one active report per target per reporter" rule is enforced by a partial unique index in the
 * database (not by a read-then-write check), so a double-submitted report collides instead of creating
 * duplicate moderation work.
 */
public interface ReportRepository extends JpaRepository<Report, UUID> {

    long countByReporterIdAndCreatedAtAfter(UUID reporterId, Instant after);

    @Query("""
            select r from Report r
            where r.reporterId = :reporterId
              and r.targetType = :targetType
              and ((:targetUserId is not null and r.targetUserId = :targetUserId)
                   or (:targetUserId is null and r.targetUserId is null))
              and ((:targetMessageId is not null and r.targetMessageId = :targetMessageId)
                   or (:targetMessageId is null and r.targetMessageId is null))
              and r.status in (com.realtimechat.moderation.entity.ReportStatus.OPEN,
                               com.realtimechat.moderation.entity.ReportStatus.REVIEWING)
            """)
    List<Report> findActiveDuplicate(@Param("reporterId") UUID reporterId,
                                     @Param("targetType") ReportTargetType targetType,
                                     @Param("targetUserId") UUID targetUserId,
                                     @Param("targetMessageId") UUID targetMessageId);

    /** Moderation queue, oldest first so nothing rots at the bottom. */
    @Query("""
            select r from Report r
            where (:status is null or r.status = :status)
            order by r.createdAt asc, r.id asc
            """)
    List<Report> findQueue(@Param("status") ReportStatus status, Pageable pageable);

    @Query("""
            select r from Report r
            where (:status is null or r.status = :status)
              and (r.createdAt > :cursorCreatedAt
                   or (r.createdAt = :cursorCreatedAt and r.id > :cursorId))
            order by r.createdAt asc, r.id asc
            """)
    List<Report> findQueueAfter(@Param("status") ReportStatus status,
                               @Param("cursorCreatedAt") Instant cursorCreatedAt,
                               @Param("cursorId") UUID cursorId,
                               Pageable pageable);

    @Query("select count(r) from Report r where r.status = :status")
    long countByStatus(@Param("status") ReportStatus status);

    @Query("""
            select r from Report r where r.reporterId = :reporterId
            order by r.createdAt desc, r.id desc
            """)
    List<Report> findByReporter(@Param("reporterId") UUID reporterId, Pageable pageable);
}
