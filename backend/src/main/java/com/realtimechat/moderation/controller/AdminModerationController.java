package com.realtimechat.moderation.controller;

import com.realtimechat.common.pagination.PageResponse;
import com.realtimechat.common.web.AuthenticatedUser;
import com.realtimechat.config.AppProperties;
import com.realtimechat.moderation.dto.AdminReportResponse;
import com.realtimechat.moderation.dto.SuspendUserRequest;
import com.realtimechat.moderation.dto.UpdateReportStatusRequest;
import com.realtimechat.moderation.service.ModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Moderator endpoints.
 *
 * <p>All of them require {@code ROLE_ADMIN} (enforced by the security filter chain on
 * {@code /api/v1/admin/**}), and every state change is written to the audit trail with the acting
 * moderator's id. Moderators never silently edit user data: they change a report's status and, at
 * most, an account's ability to sign in.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin moderation", description = "Moderation queue and account actions (ADMIN role)")
public class AdminModerationController {

    private final ModerationService moderationService;
    private final AppProperties properties;

    public AdminModerationController(ModerationService moderationService, AppProperties properties) {
        this.moderationService = moderationService;
        this.properties = properties;
    }

    @GetMapping("/reports")
    @Operation(summary = "Moderation queue, oldest report first")
    public ResponseEntity<PageResponse<AdminReportResponse>> queue(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "limit", required = false) Integer limit) {
        int pageSize = properties.getPagination().resolveLimit(limit);
        return ResponseEntity.ok(moderationService.queue(status, cursor, pageSize));
    }

    @PatchMapping("/reports/{reportId}")
    @Operation(summary = "Move a report to REVIEWING/RESOLVED/DISMISSED (audited)")
    public ResponseEntity<AdminReportResponse> update(@PathVariable UUID reportId,
                                                     @Valid @RequestBody UpdateReportStatusRequest request,
                                                     AuthenticatedUser currentUser) {
        return ResponseEntity.ok(moderationService.updateStatus(currentUser.userId(), reportId, request));
    }

    @GetMapping("/reports/{reportId}/history")
    @Operation(summary = "Audit trail of a report")
    public ResponseEntity<List<Map<String, Object>>> history(@PathVariable UUID reportId) {
        return ResponseEntity.ok(moderationService.history(reportId));
    }

    @GetMapping("/reports/counts")
    @Operation(summary = "Queue depth by status")
    public ResponseEntity<Map<String, Long>> counts() {
        return ResponseEntity.ok(moderationService.queueCounts());
    }

    @PostMapping("/users/{userId}/suspend")
    @Operation(summary = "Suspend or reinstate an account; suspension revokes all sessions immediately")
    public ResponseEntity<Void> suspend(@PathVariable UUID userId,
                                       @Valid @RequestBody SuspendUserRequest request,
                                       AuthenticatedUser currentUser) {
        moderationService.suspendUser(currentUser.userId(), userId, request);
        return ResponseEntity.noContent().build();
    }
}
