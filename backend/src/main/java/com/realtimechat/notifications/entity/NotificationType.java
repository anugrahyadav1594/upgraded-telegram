package com.realtimechat.notifications.entity;

/** Notification kinds, matching the {@code notifications_type_valid} constraint in V5. */
public enum NotificationType {
    NEW_MESSAGE,
    CONVERSATION_CREATED,
    MODERATION_UPDATE,
    SYSTEM
}
