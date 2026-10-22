package com.realtimechat.notifications.dto;

/**
 * {@code PATCH /api/v1/notifications/settings} body.
 *
 * <p>Boxed booleans: {@code null} means "leave unchanged", so a client can toggle one switch without
 * echoing the rest and accidentally reverting a setting it never showed.
 */
public record UpdateNotificationSettingsRequest(
        Boolean newMessage,
        Boolean mentions,
        Boolean conversationUpdates,
        Boolean moderationUpdates,
        Boolean emailDigest
) {
}
