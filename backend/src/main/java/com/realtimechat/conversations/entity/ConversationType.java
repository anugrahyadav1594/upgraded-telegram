package com.realtimechat.conversations.entity;

/**
 * Conversation kinds supported by the schema.
 *
 * <p>Only {@link #DIRECT} is produced by the product today, but the storage model and the uniqueness
 * rule are written so that adding groups does not require a destructive migration — see
 * {@code docs/database-design.md}. Anything the code does not implement is still <em>rejected</em>
 * rather than silently accepted.
 */
public enum ConversationType {
    DIRECT,
    GROUP,
    CHANNEL
}
