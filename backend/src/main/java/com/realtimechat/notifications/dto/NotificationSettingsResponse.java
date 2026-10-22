package com.realtimechat.notifications.dto;

import com.realtimechat.notifications.entity.NotificationSettings;

/**
 * Notification preferences.
 *
 * <p>{@code newMessage} and {@code conversationUpdates} are separate on purpose: "tell me about
 * messages" and "tell me about conversation changes" are different amounts of noise, and users who
 * want the first almost never want the second.
 */
public record NotificationSettingsResponse(
        boolean newMessage,
        boolean mentions,
        boolean conversationUpdates,
        boolean moderationUpdates,
        boolean emailDigest
) {

    public static NotificationSettingsResponse from(NotificationSettings settings) {
        return new NotificationSettingsResponse(settings.isNewMessage(), settings.isMentions(),
                settings.isConversationUpdates(), settings.isModerationUpdates(), settings.isEmailDigest());
    }
}
