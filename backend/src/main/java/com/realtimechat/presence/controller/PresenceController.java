package com.realtimechat.presence.controller;

import com.realtimechat.common.web.AuthenticatedUser;
import com.realtimechat.presence.dto.PresenceResponse;
import com.realtimechat.presence.dto.PresenceSnapshot;
import com.realtimechat.presence.service.PresenceService;
import com.realtimechat.users.service.BlockQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST presence endpoint.
 *
 * <p>The socket remains the primary channel ({@code presence.updated} events); this endpoint exists
 * for the cases a push model cannot cover: the initial page load, a client that reconnects, and
 * clients whose socket is unavailable. It returns the same filtered view, so REST and WebSocket can
 * never disagree about privacy.
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/presence")
@Tag(name = "Presence", description = "Online status and last-seen (privacy filtered)")
public class PresenceController {

    private final PresenceService presenceService;
    private final BlockQueryService blockQueryService;

    public PresenceController(PresenceService presenceService, BlockQueryService blockQueryService) {
        this.presenceService = presenceService;
        this.blockQueryService = blockQueryService;
    }

    @GetMapping
    @Operation(summary = "Presence of a user, filtered by their privacy settings and block rules")
    public ResponseEntity<PresenceResponse> presence(@PathVariable UUID userId,
                                                    AuthenticatedUser currentUser) {
        boolean blocked = blockQueryService.isBlockedEitherWay(currentUser.userId(), userId);
        return presenceService.snapshot(userId, blocked)
                .map(snapshot -> ResponseEntity.ok(toResponse(snapshot)))
                // A block relationship is indistinguishable from a missing user on purpose.
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private PresenceResponse toResponse(PresenceSnapshot snapshot) {
        boolean visible = snapshot.status() != com.realtimechat.presence.dto.PresenceStatus.HIDDEN;
        return new PresenceResponse(
                snapshot.userId(),
                visible ? snapshot.status().name() : null,
                visible ? snapshot.lastSeenAt() : null,
                visible);
    }
}
