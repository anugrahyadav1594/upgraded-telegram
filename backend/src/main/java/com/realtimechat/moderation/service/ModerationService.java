package com.realtimechat.moderation.service;

import com.realtimechat.common.error.ConflictException;
import com.realtimechat.common.error.ForbiddenException;
import com.realtimechat.common.error.InvalidRequestException;
import com.realtimechat.common.error.NotFoundException;
import com.realtimechat.common.error.RateLimitExceededException;
import com.realtimechat.common.pagination.Cursor;
import com.realtimechat.common.pagination.CursorCodec;
import com.realtimechat.common.pagination.PageResponse;
import com.realtimechat.common.ratelimit.RateLimitDecision;
import com.realtimechat.common.ratelimit.RateLimiter;
import com.realtimechat.common.realtime.RealtimeBroadcaster;
import com.realtimechat.common.realtime.RealtimeEvent;
import com.realtimechat.common.realtime.RealtimeEventType;
import com.realtimechat.common.util.TextNormalizer;
import com.realtimechat.common.util.TimeProvider;
import com.realtimechat.config.AppProperties;
import com.realtimechat.conversations.service.ConversationAccessPort;
import com.realtimechat.messaging.service.MessageLookupService;
import com.realtimechat.moderation.dto.AdminReportResponse;
import com.realtimechat.moderation.dto.CreateReportRequest;
import com.realtimechat.moderation.dto.ReportResponse;
import com.realtimechat.moderation.dto.SuspendUserRequest;
import com.realtimechat.moderation.dto.UpdateReportStatusRequest;
import com.realtimechat.moderation.entity.Report;
import com.realtimechat.moderation.entity.ReportReason;
import com.realtimechat.moderation.entity.ReportStatus;
import com.realtimechat.moderation.entity.ReportStatusHistory;
import com.realtimechat.moderation.entity.ReportTargetType;
import com.realtimechat.moderation.repository.ReportRepository;
import com.realtimechat.moderation.repository.ReportStatusHistoryRepository;
import com.realtimechat.notifications.service.NotificationService;
import com.realtimechat.users.entity.User;
import com.realtimechat.users.repository.UserRepository;
import com.realtimechat.users.service.SessionRevocationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reporting and moderation.
 *
 * <p>Trust boundaries, stated explicitly:
 * <ul>
 *   <li>a report is <b>evidence, not proof</b> — it never triggers an automatic action against the
 *       reported user (no auto-ban, no auto-hide), because a report is trivially forgeable and the
 *       cost of a false positive is somebody being silenced by an attacker;</li>
 *   <li>the reported user is <b>never</b> told they were reported;</li>
 *   <li>the reporter's identity is visible to moderators only, and only through the admin endpoints;</li>
 *   <li>every state change writes an audit row, so "who did what" is answerable months later.</li>
 * </ul>
 */
@Service
public class ModerationService {

    private static final Logger log = LoggerFactory.getLogger(ModerationService.class);
    private static final Logger securityLog = LoggerFactory.getLogger("SECURITY");
    private static final String BUCKET_REPORTS = "report";
    private static final int MAX_PAGE = 100;

    private final ReportRepository reportRepository;
    private final ReportStatusHistoryRepository historyRepository;
    private final ConversationAccessPort conversationAccessPort;
    private final MessageLookupService messageLookupService;
    private final NotificationService notificationService;
    private final SessionRevocationPort sessionRevocationPort;
    private final UserRepository userRepository;
    private final RateLimiter rateLimiter;
    private final RealtimeBroadcaster broadcaster;
    private final AppProperties properties;
    private final TimeProvider timeProvider;
    private final CursorCodec cursorCodec;

    public ModerationService(ReportRepository reportRepository,
                             ReportStatusHistoryRepository historyRepository,
                             ConversationAccessPort conversationAccessPort,
                             MessageLookupService messageLookupService,
                             NotificationService notificationService,
                             SessionRevocationPort sessionRevocationPort,
                             UserRepository userRepository,
                             RateLimiter rateLimiter,
                             RealtimeBroadcaster broadcaster,
                             AppProperties properties,
                             TimeProvider timeProvider,
                             CursorCodec cursorCodec) {
        this.reportRepository = reportRepository;
        this.historyRepository = historyRepository;
        this.conversationAccessPort = conversationAccessPort;
        this.messageLookupService = messageLookupService;
        this.notificationService = notificationService;
        this.sessionRevocationPort = sessionRevocationPort;
        this.userRepository = userRepository;
        this.rateLimiter = rateLimiter;
        this.broadcaster = broadcaster;
        this.properties = properties;
        this.timeProvider = timeProvider;
        this.cursorCodec = cursorCodec;
    }

    // ------------------------------------------------------------------ reporting

    /**
     * Files a report.
     *
     * <p>Validation order matters for safety: the caller cannot report themselves, must be able to see
     * the target, and only then may the report be created. A report about a message the reporter cannot
     * access would turn the endpoint into a probe for "does this message id exist?".
     */
    @Transactional
    public ReportResponse createReport(UUID reporterId, CreateReportRequest request) {
        ReportTargetType targetType;
        try {
            targetType = ReportTargetType.valueOf(request.targetType().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("Unknown target type");
        }
        ReportReason reason;
        try {
            reason = ReportReason.valueOf(request.reason().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("Unknown reason");
        }
        String description = TextNormalizer.normalizeText(request.description());

        if (targetType == ReportTargetType.USER) {
            if (request.targetUserId() == null) {
                throw new InvalidRequestException("targetUserId is required when reporting a user");
            }
            if (request.targetUserId().equals(reporterId)) {
                throw new InvalidRequestException("You cannot report yourself");
            }
        } else if (request.targetMessageId() == null) {
            throw new InvalidRequestException("targetMessageId is required when reporting a message");
        }

        // Access check: reporting a message requires being in its conversation. The report is about a
        // conversation the reporter participates in, so this is a membership question, not a content
        // read — the message body is never returned to the reporter through this path.
        if (targetType == ReportTargetType.MESSAGE) {
            UUID conversationId = resolveConversationOfMessage(request.targetMessageId());
            if (conversationId == null || !conversationAccessPort.isParticipant(conversationId, reporterId)) {
                throw new ForbiddenException("You cannot report that message");
            }
        }

        enforceReportRateLimit(reporterId);

        Report report = targetType == ReportTargetType.USER
                ? Report.forUser(reporterId, request.targetUserId(), reason, description)
                : Report.forMessage(reporterId, request.targetMessageId(), request.targetUserId(), reason,
                        description);
        try {
            reportRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("You have already reported this");
        }
        historyRepository.save(ReportStatusHistory.of(report.getId(), null, ReportStatus.OPEN, reporterId,
                "Report submitted"));
        securityLog.info("report filed type={} reason={}", targetType, reason);
        return ReportResponse.from(report);
    }

    /**
     * Finds the conversation a message belongs to.
     *
     * <p>The lookup goes through the messaging module's own service rather than a direct repository
     * read, so the messaging schema stays owned by one module; the access check that follows is what
     * actually decides whether the report may be filed.
     */
    private UUID resolveConversationOfMessage(UUID messageId) {
        return messageLookupService.conversationOf(messageId).orElse(null);
    }

    private void enforceReportRateLimit(UUID reporterId) {
        AppProperties.RateLimit limits = properties.getRateLimit();
        if (!limits.isEnabled()) {
            return;
        }
        RateLimitDecision decision = rateLimiter.consume(BUCKET_REPORTS, reporterId.toString(),
                limits.getReportsPerDay(), Duration.ofDays(1));
        if (!decision.allowed()) {
            throw new RateLimitExceededException("You have reached the daily report limit.");
        }
    }

    /** The reporter's own submissions — so they can see that a report was received. */
    @Transactional(readOnly = true)
    public List<ReportResponse> myReports(UUID reporterId, int limit) {
        int pageSize = Math.min(Math.max(limit, 1), MAX_PAGE);
        return reportRepository.findByReporter(reporterId, PageRequest.of(0, pageSize)).stream()
                .map(ReportResponse::from)
                .toList();
    }

    // ------------------------------------------------------------------ moderation queue

    @Transactional(readOnly = true)
    public PageResponse<AdminReportResponse> queue(String rawStatus, String rawCursor, int limit) {
        int pageSize = Math.min(Math.max(limit, 1), MAX_PAGE);
        ReportStatus status = parseStatus(rawStatus);
        Cursor cursor = cursorCodec.decode(rawCursor);
        List<Report> reports = cursor == null
                ? reportRepository.findQueue(status, PageRequest.of(0, pageSize + 1))
                : reportRepository.findQueueAfter(status, cursor.instantKey(), cursor.id(),
                        PageRequest.of(0, pageSize + 1));
        List<AdminReportResponse> items = reports.stream().map(AdminReportResponse::from).toList();
        return PageResponse.of(items, pageSize,
                response -> Cursor.ofInstant(response.createdAt(), response.id()), cursorCodec);
    }

    /**
     * Applies a moderator's decision.
     *
     * <p>A terminal state requires a note in practice (the UI enforces it, the audit trail records it)
     * and always notifies the <b>reporter</b> — never the reported user, whose only signal is a change
     * in what the product lets them do.
     */
    @Transactional
    public AdminReportResponse updateStatus(UUID moderatorId, UUID reportId, UpdateReportStatusRequest request) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new NotFoundException("Report not found"));
        ReportStatus next = parseStatus(request.status());
        if (report.getStatus() == next) {
            return AdminReportResponse.from(report); // idempotent: re-applying a decision is not an error
        }
        if (next == ReportStatus.RESOLVED && (request.note() == null || request.note().isBlank())) {
            throw new InvalidRequestException("A resolution note is required when resolving a report");
        }
        ReportStatus previous = report.getStatus();
        Instant now = timeProvider.now();
        if (next == ReportStatus.REVIEWING) {
            report.startReview(moderatorId, now);
        } else {
            report.transitionTo(next, moderatorId, TextNormalizer.normalizeText(request.note()), now);
        }
        reportRepository.save(report);
        historyRepository.save(ReportStatusHistory.of(reportId, previous, next, moderatorId,
                TextNormalizer.normalizeText(request.note())));
        log.info("report status changed reportId={} from={} to={}", reportId, previous, next);

        notificationService.notifyModerationUpdate(report.getReporterId(),
                "Your report was " + next.name().toLowerCase(java.util.Locale.ROOT));
        return AdminReportResponse.from(report);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(UUID reportId) {
        reportRepository.findById(reportId).orElseThrow(() -> new NotFoundException("Report not found"));
        return historyRepository.findByReportIdOrderByCreatedAtAsc(reportId).stream()
                .map(entry -> {
                    Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("id", entry.getId());
                    row.put("fromStatus", entry.getFromStatus() == null ? null : entry.getFromStatus().name());
                    row.put("toStatus", entry.getToStatus().name());
                    row.put("actorId", entry.getActorId());
                    row.put("note", entry.getNote());
                    row.put("createdAt", entry.getCreatedAt());
                    return row;
                })
                .toList();
    }

    // ------------------------------------------------------------------ account actions

    /**
     * Suspends or reinstates an account.
     *
     * <p>Suspension takes effect immediately for existing sessions: revoking them is the difference
     * between "cannot log in again" and "cannot act right now", and only the second is what a
     * moderation action is supposed to mean.
     */
    @Transactional
    public void suspendUser(UUID moderatorId, UUID targetUserId, SuspendUserRequest request) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (user.isTombstone()) {
            throw new InvalidRequestException("That account cannot be moderated");
        }
        boolean reinstate = "REINSTATE".equalsIgnoreCase(request.action());
        if (reinstate) {
            user.reinstate();
            userRepository.save(user);
            log.info("account reinstated userId={} by={}", targetUserId, moderatorId);
            notificationService.notifyModerationUpdate(targetUserId, "Your account has been reinstated");
            return;
        }
        user.suspendUntil(request.until());
        userRepository.save(user);
        int revoked = sessionRevocationPort.revokeAll(targetUserId);
        securityLog.info("account suspended userId={} by={} until={} sessionsRevoked={}",
                targetUserId, moderatorId, request.until(), revoked);
        broadcaster.publish(RealtimeEvent.toUser(RealtimeEventType.SESSION_REVOKED,
                Map.of("reason", "ACCOUNT_SUSPENDED"), targetUserId));
        notificationService.notifyModerationUpdate(targetUserId, "Your account has been suspended");
    }

    /** Counts for the moderation dashboard (also exported through metrics in the admin controller). */
    @Transactional(readOnly = true)
    public Map<String, Long> queueCounts() {
        return Map.of(
                "open", reportRepository.countByStatus(ReportStatus.OPEN),
                "reviewing", reportRepository.countByStatus(ReportStatus.REVIEWING));
    }

    private ReportStatus parseStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return null;
        }
        try {
            return ReportStatus.valueOf(rawStatus.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("Unknown status filter");
        }
    }

    /** Used by tests and diagnostics. */
    @Transactional(readOnly = true)
    public Optional<Report> find(UUID reportId) {
        return reportRepository.findById(reportId);
    }
}
