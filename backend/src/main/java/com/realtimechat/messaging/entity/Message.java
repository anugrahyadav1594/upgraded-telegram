package com.realtimechat.messaging.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A message.
 *
 * <p>Design points:
 * <ul>
 *   <li><b>Append-only in practice.</b> Edit and delete are rare UPDATEs; the history index is
 *       {@code (conversation_id, created_at DESC, id DESC)} which is exactly the keyset pagination
 *       order, so a deep page costs the same as the first and {@code OFFSET} is never needed.</li>
 *   <li><b>{@code sequence} is a resync watermark.</b> Assigned by a global sequence at insert, it
 *       lets a reconnecting client ask for "everything newer than X" in one query without relying on
 *       timestamps (which can be equal or skewed).</li>
 *   <li><b>{@code client_message_id} is the idempotency key.</b> A retried send (the classic mobile
 *       case: the reply was lost, the client retries) hits the unique index and is resolved to the
 *       existing message instead of creating a duplicate. It is also what lets the UI reconcile an
 *       optimistic bubble with the server's version.</li>
 *   <li><b>Deletion is a tombstone.</b> {@code deleted_at} plus scrubbed content keeps the
 *       conversation's shape and the reply graph intact; recipients' receipts and the ordering are
 *       preserved.</li>
 *   <li><b>Delivery state is not here.</b> It belongs to {@link MessageReceipt}, one row per
 *       recipient, because a message can be read by one participant and unread by another (and by
 *       more than one once groups exist).</li>
 * </ul>
 */
@Entity
@Table(name = "messages")
public class Message {

    /** Tombstone content; never user-authored, so it cannot be confused with a real message. */
    public static final String DELETED_CONTENT = "[deleted]";

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    @Column(name = "sender_id", nullable = false, updatable = false)
    private UUID senderId;

    @Column(name = "content")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 16)
    private MessageType messageType = MessageType.TEXT;

    @Column(name = "reply_to_message_id")
    private UUID replyToMessageId;

    @Column(name = "client_message_id")
    private UUID clientMessageId;

    /**
     * Assigned by the database sequence {@code messages_sequence} on insert.
     * Declared read-only for Hibernate so the database remains the single source of ordering.
     */
    @Column(name = "sequence", nullable = false, insertable = false, updatable = false)
    private Long sequence;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Message() {
        // for JPA
    }

    /**
     * Creates a text message.
     *
     * @param clientMessageId client-generated idempotency key (required for user-authored messages
     *                        so that retries are safe even when the WebSocket reply was lost)
     */
    public static Message createText(UUID conversationId, UUID senderId, String content,
                                     UUID clientMessageId, UUID replyToMessageId) {
        Message message = new Message();
        message.id = UUID.randomUUID();
        message.conversationId = conversationId;
        message.senderId = senderId;
        message.content = content;
        message.messageType = MessageType.TEXT;
        message.clientMessageId = clientMessageId;
        message.replyToMessageId = replyToMessageId;
        return message;
    }

    /**
     * Applies an edit.
     *
     * <p>Only the author may call this (enforced in the service), and only within the edit window —
     * after which the message stands as part of the conversation record.
     */
    public void edit(String newContent, Instant when) {
        this.content = newContent;
        this.editedAt = when;
    }

    /**
     * Tombstones the message.
     *
     * <p>Content is replaced rather than merely flagged so that a "deleted" message cannot be
     * recovered from the API, the search index, or a database dump taken later.
     */
    public void softDelete(Instant when) {
        this.deletedAt = when;
        this.content = DELETED_CONTENT;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isEdited() {
        return editedAt != null;
    }

    /** True when the edit window (configurable, 48 h by default) has elapsed. */
    public boolean isEditableAt(Instant now, java.time.Duration editWindow) {
        return !isDeleted() && createdAt != null && createdAt.plus(editWindow).isAfter(now);
    }

    /** True when the author may remove the message; deletion has no time limit, by design. */
    public boolean isDeletableBy(UUID userId) {
        return senderId.equals(userId) && !isDeleted();
    }

    public UUID getId() {
        return id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public UUID getSenderId() {
        return senderId;
    }

    public String getContent() {
        return content;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public UUID getReplyToMessageId() {
        return replyToMessageId;
    }

    public UUID getClientMessageId() {
        return clientMessageId;
    }

    public Long getSequence() {
        return sequence;
    }

    public Instant getEditedAt() {
        return editedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
