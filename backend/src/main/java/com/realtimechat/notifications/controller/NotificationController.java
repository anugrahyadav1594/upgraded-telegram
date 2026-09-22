package com.realtimechat.notifications.controller;

import com.realtimechat.common.pagination.PageResponse;
import com.realtimechat.common.web.AuthenticatedUser;
import com.realtimechat.config.AppProperties;
import com.realtimechat.notifications.dto.MarkNotificationsReadRequest;
import com.realtimechat.notifications.dto.NotificationResponse;
import com.realtimechat.notifications.dto.NotificationSettingsResponse;
import com.realtimechat.notifications.dto.UpdateNotificationSettingsRequest;
import com.realtimechat.notifications.entity.NotificationSettings;
import com.realtimechat.notifications.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Notification centre endpoints.
 *
 * <p>Every handler works on the authenticated user's own rows — there is no endpoint that takes a user
 * id — and the unread count is returned alongside mutations so the badge never needs a second request.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "Notification centre and preferences")
public class NotificationController {

    private final NotificationService notificationService;
    private final AppProperties properties;

    public NotificationController(NotificationService notificationService, AppProperties properties) {
        this.notificationService = notificationService;
        this.properties = properties;
    }

    @GetMapping
    @Operation(summary = "List notifications, newest first (cursor paginated)")
    public ResponseEntity<PageResponse<NotificationResponse>> list(
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "unreadOnly", required = false, defaultValue = "false") boolean unreadOnly,
            AuthenticatedUser currentUser) {
        int pageSize = properties.getPagination().resolveLimit(limit);
        return ResponseEntity.ok(notificationService.list(currentUser.userId(), cursor, pageSize, unreadOnly));
    }

    @PostMapping("/read")
    @Operation(summary = "Mark specific notifications read, or all of them when ids is omitted")
    public ResponseEntity<Map<String, Long>> markRead(@RequestBody(required = false) MarkNotificationsReadRequest request,
                                                      AuthenticatedUser currentUser) {
        long remaining = notificationService.markRead(currentUser.userId(),
                request == null ? null : request.ids());
        return ResponseEntity.ok(Map.of("unreadCount", remaining));
    }

    @DeleteMapping("/{notificationId}")
    @Operation(summary = "Delete one of your notifications")
    public ResponseEntity<Void> delete(@PathVariable UUID notificationId, AuthenticatedUser currentUser) {
        notificationService.delete(currentUser.userId(), notificationId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    @Operation(summary = "Clear the notification centre")
    public ResponseEntity<Void> clear(AuthenticatedUser currentUser) {
        notificationService.deleteAll(currentUser.userId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/settings")
    @Operation(summary = "Read notification preferences")
    public ResponseEntity<NotificationSettingsResponse> settings(AuthenticatedUser currentUser) {
        return ResponseEntity.ok(NotificationSettingsResponse.from(
                notificationService.settings(currentUser.userId())));
    }

    @PatchMapping("/settings")
    @Operation(summary = "Update notification preferences (partial)")
    public ResponseEntity<NotificationSettingsResponse> updateSettings(
            @RequestBody UpdateNotificationSettingsRequest request,
            AuthenticatedUser currentUser) {
        NotificationSettings current = notificationService.settings(currentUser.userId());
        return ResponseEntity.ok(notificationService.updateSettings(
                currentUser.userId(),
                request.newMessage() == null ? current.isNewMessage() : request.newMessage(),
                request.mentions() == null ? current.isMentions() : request.mentions(),
                request.conversationUpdates() == null ? current.isConversationUpdates()
                        : request.conversationUpdates(),
                request.moderationUpdates() == null ? current.isModerationUpdates()
                        : request.moderationUpdates(),
                request.emailDigest() == null ? current.isEmailDigest() : request.emailDigest()));
    }
}
