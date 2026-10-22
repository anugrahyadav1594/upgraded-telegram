package com.realtimechat.conversations.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One participant's private view of a conversation.
 *
 * <p>Unread state, read cursor, mute, archive and per-user delete are all here — never on the
 * conversation row — because the two participants have independent values for every one of them.
 * Putting {@code unread_count_a} / {@code unread_count_b} on {@code conversations} would work for
 * exactly as long as the product stays one-to-one.
 *
 * <p>Note there is no {@code unread_count} column: the badge is derived from
 * {@code message_receipts} (partial index on unread rows), and this row only stores the coarse read
 * cursor used for list rendering. A derived count cannot drift out of sync with the receipts that
 * define "unread" in the first place.
 */
@Entity
@Table(name = "conversation_participants")
public class ConversationParticipant {

    @EmbeddedId
    private Key id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("conversationId")
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt = Instant.now();

    @Column(name = "last_read_message_id")
    private UUID lastReadMessageId;

    @Column(name = "last_read_at")
    private Instant lastReadAt;

    @Column(name = "muted", nullable = false)
    private boolean muted;

    @Column(name = "archived", nullable = false)
    private boolean archived;

    /** Per-user delete. A new incoming message revives the conversation (see the service). */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ConversationParticipant() {
        // for JPA
    }

    ConversationParticipant(Conversation conversation, UUID userId) {
        this.conversation = conversation;
        this.id = new Key(conversation.getId(), userId);
        this.joinedAt = Instant.now();
    }

    /**
     * Moves the read cursor to a message.
     *
     * <p>Monotonic: WebSocket frames can arrive out of order, and a late "mark read" for an older
     * message must not resurrect an unread badge. Returns whether anything changed so the caller can
     * skip a pointless UPDATE.
     */
    public boolean markRead(UUID messageId, Instant readAt) {
        if (lastReadAt != null && readAt.isBefore(lastReadAt)) {
            return false;
        }
        if (messageId.equals(lastReadMessageId)) {
            return false;
        }
        this.lastReadMessageId = messageId;
        this.lastReadAt = readAt;
        this.deletedAt = null; // reading it means the user is looking at it again
        return true;
    }

    public boolean archive(boolean value) {
        if (this.archived == value) {
            return false;
        }
        this.archived = value;
        return true;
    }

    public boolean mute(boolean value) {
        if (this.muted == value) {
            return false;
        }
        this.muted = value;
        return true;
    }

    public boolean softDelete(Instant when) {
        if (deletedAt != null) {
            return false;
        }
        this.deletedAt = when;
        return true;
    }

    /** A message arrived: an archived conversation stays archived, a deleted one comes back. */
    public void onIncomingMessage() {
        this.deletedAt = null;
    }

    public void restore() {
        this.deletedAt = null;
    }

    public Key getId() {
        return id;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public UUID getUserId() {
        return id.getUserId();
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public UUID getLastReadMessageId() {
        return lastReadMessageId;
    }

    public Instant getLastReadAt() {
        return lastReadAt;
    }

    public boolean isMuted() {
        return muted;
    }

    public boolean isArchived() {
        return archived;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Composite primary key: exactly one row per (conversation, user). */
    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "conversation_id", nullable = false)
        private UUID conversationId;

        @Column(name = "user_id", nullable = false)
        private UUID userId;

        public Key() {
        }

        public Key(UUID conversationId, UUID userId) {
            this.conversationId = conversationId;
            this.userId = userId;
        }

        public UUID getConversationId() {
            return conversationId;
        }

        public UUID getUserId() {
            return userId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key key)) {
                return false;
            }
            return Objects.equals(conversationId, key.conversationId) && Objects.equals(userId, key.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(conversationId, userId);
        }
    }
}
