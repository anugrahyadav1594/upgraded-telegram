package com.realtimechat.messaging.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One emoji reaction by one user on one message.
 *
 * <p>Toggling is insert/delete rather than a boolean, so the row set <em>is</em> the current state and
 * two concurrent toggles cannot produce an inconsistent flag. The composite primary key also makes
 * "same user, same emoji, twice" impossible.
 */
@Entity
@Table(name = "message_reactions")
@IdClass(MessageReaction.Key.class)
public class MessageReaction {

    @Id
    @Column(name = "message_id", nullable = false, updatable = false)
    private UUID messageId;

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Id
    @Column(name = "emoji", nullable = false, length = 16, updatable = false)
    private String emoji;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MessageReaction() {
        // for JPA
    }

    public MessageReaction(UUID messageId, UUID userId, String emoji) {
        this.messageId = messageId;
        this.userId = userId;
        this.emoji = emoji;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEmoji() {
        return emoji;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Composite key {@code (messageId, userId, emoji)}. */
    public static class Key implements Serializable {

        private UUID messageId;
        private UUID userId;
        private String emoji;

        public Key() {
        }

        public Key(UUID messageId, UUID userId, String emoji) {
            this.messageId = messageId;
            this.userId = userId;
            this.emoji = emoji;
        }

        public UUID getMessageId() {
            return messageId;
        }

        public UUID getUserId() {
            return userId;
        }

        public String getEmoji() {
            return emoji;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(messageId, key.messageId)
                    && Objects.equals(userId, key.userId)
                    && Objects.equals(emoji, key.emoji);
        }

        @Override
        public int hashCode() {
            return Objects.hash(messageId, userId, emoji);
        }
    }
}
