package com.realtimechat.moderation.controller;

import com.realtimechat.common.web.AuthenticatedUser;
import com.realtimechat.moderation.dto.CreateReportRequest;
import com.realtimechat.moderation.dto.ReportResponse;
import com.realtimechat.moderation.service.ModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Reporting endpoint.
 *
 * <p>The reporter's identity comes from the session, never the body; the reported user is never
 * notified, and the reporter can see their own submissions so "did that report go through?" has an
 * answer without exposing anybody else's reports.
 */
@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Moderation", description = "Report a user or a message")
public class ReportController {

    private static final int MAX_MY_REPORTS = 50;

    private final ModerationService moderationService;

    public ReportController(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @PostMapping
    @Operation(summary = "Report a user or a message")
    public ResponseEntity<ReportResponse> create(@Valid @RequestBody CreateReportRequest request,
                                                AuthenticatedUser currentUser) {
        ReportResponse response = moderationService.createReport(currentUser.userId(), request);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(response);
    }

    @GetMapping("/mine")
    @Operation(summary = "Your own submitted reports and their status")
    public ResponseEntity<List<ReportResponse>> mine(
            @RequestParam(name = "limit", required = false, defaultValue = "20") int limit,
            AuthenticatedUser currentUser) {
        return ResponseEntity.ok(moderationService.myReports(currentUser.userId(),
                Math.min(limit, MAX_MY_REPORTS)));
    }
}
