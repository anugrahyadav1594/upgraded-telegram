package com.realtimechat.notifications.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A durable in-app notification.
 *
 * <p>Durable, but strictly derived: losing a notification costs a convenience, never data — the
 * conversation and the message are the source of truth. That is what makes it safe to create them
 * asynchronously and to prune them after 90 days.
 *
 * <p>{@code preview} is truncated to 120 characters by the schema, so a notification can never become
 * a second full copy of a message body (which would mean two places to erase on deletion and two
 * places to leak from).
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private NotificationType type;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "message_id")
    private UUID messageId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "preview", length = 120)
    private String preview;

    @Column(name = "read_at")
    private Instant readAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {
        // for JPA
    }

    public static Notification newMessage(UUID userId, UUID conversationId, UUID messageId, UUID actorId,
                                          String preview) {
        Notification notification = new Notification();
        notification.id = UUID.randomUUID();
        notification.userId = userId;
        notification.type = NotificationType.NEW_MESSAGE;
        notification.conversationId = conversationId;
        notification.messageId = messageId;
        notification.actorId = actorId;
        notification.preview = preview == null || preview.length() <= 120 ? preview : preview.substring(0, 120);
        return notification;
    }

    public static Notification moderation(UUID userId, String summary) {
        Notification notification = new Notification();
        notification.id = UUID.randomUUID();
        notification.userId = userId;
        notification.type = NotificationType.MODERATION_UPDATE;
        notification.preview = summary == null || summary.length() <= 120 ? summary : summary.substring(0, 120);
        return notification;
    }

    public static Notification system(UUID userId, String summary) {
        Notification notification = new Notification();
        notification.id = UUID.randomUUID();
        notification.userId = userId;
        notification.type = NotificationType.SYSTEM;
        notification.preview = summary == null || summary.length() <= 120 ? summary : summary.substring(0, 120);
        return notification;
    }

    /** Idempotent: marking an already-read notification returns false so callers can skip a write. */
    public boolean markRead(Instant when) {
        if (readAt != null) {
            return false;
        }
        this.readAt = when;
        return true;
    }

    public boolean isRead() {
        return readAt != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public NotificationType getType() {
        return type;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getPreview() {
        return preview;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
