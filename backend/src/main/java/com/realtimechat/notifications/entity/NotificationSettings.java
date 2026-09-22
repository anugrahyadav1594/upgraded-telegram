package com.realtimechat.notifications.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-user notification preferences.
 *
 * <p>Defaults are the "useful but not noisy" set: new messages on, conversation chatter off. A row may
 * be missing (settings are created lazily); absent means {@link #defaultsFor} — never "everything
 * enabled", so a missing row cannot accidentally subscribe a user to more noise.
 */
@Entity
@Table(name = "user_notification_settings")
public class NotificationSettings {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "new_message", nullable = false)
    private boolean newMessage = true;

    @Column(name = "mentions", nullable = false)
    private boolean mentions = true;

    @Column(name = "conversation_updates", nullable = false)
    private boolean conversationUpdates;

    @Column(name = "moderation_updates", nullable = false)
    private boolean moderationUpdates = true;

    @Column(name = "email_digest", nullable = false)
    private boolean emailDigest;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationSettings() {
        // for JPA
    }

    public static NotificationSettings defaultsFor(UUID userId) {
        NotificationSettings settings = new NotificationSettings();
        settings.userId = userId;
        return settings;
    }

    public void update(boolean newMessage, boolean mentions, boolean conversationUpdates,
                       boolean moderationUpdates, boolean emailDigest) {
        this.newMessage = newMessage;
        this.mentions = mentions;
        this.conversationUpdates = conversationUpdates;
        this.moderationUpdates = moderationUpdates;
        this.emailDigest = emailDigest;
    }

    public UUID getUserId() {
        return userId;
    }

    public boolean isNewMessage() {
        return newMessage;
    }

    public boolean isMentions() {
        return mentions;
    }

    public boolean isConversationUpdates() {
        return conversationUpdates;
    }

    public boolean isModerationUpdates() {
        return moderationUpdates;
    }

    public boolean isEmailDigest() {
        return emailDigest;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
