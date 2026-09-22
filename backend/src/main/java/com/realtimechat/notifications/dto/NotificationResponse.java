package com.realtimechat.notifications.dto;

import com.realtimechat.notifications.entity.Notification;
import com.realtimechat.users.service.ProfilePort;

import java.time.Instant;
import java.util.UUID;

/**
 * A notification as the client renders it.
 *
 * <p>The actor is embedded as a compact summary (id, handle, display name, avatar) rather than as a
 * user id the client would have to resolve: that would mean a profile request per notification row and
 * a permission question the notification already answered — the actor is somebody the recipient has a
 * conversation with, or there would be no notification.
 */
public record NotificationResponse(
        UUID id,
        String type,
        UUID conversationId,
        UUID messageId,
        ActorSummary actor,
        String preview,
        boolean read,
        Instant createdAt
) {

    public static NotificationResponse from(Notification notification, ProfilePort.UserSummary actor) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType().name(),
                notification.getConversationId(),
                notification.getMessageId(),
                actor == null ? null : new ActorSummary(actor.userId(), actor.username(), actor.displayName(),
                        actor.avatarUrl()),
                notification.getPreview(),
                notification.isRead(),
                notification.getCreatedAt());
    }

    /** Minimal actor representation; never includes an email or any private field. */
    public record ActorSummary(UUID id, String username, String displayName, String avatarUrl) {
    }
}
