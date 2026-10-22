package com.realtimechat.conversations.dto;

/**
 * Result of {@code POST /api/v1/conversations}.
 *
 * <p>The distinction between 201 and 200 is part of the contract ("created" versus "already existed"),
 * so it is carried explicitly instead of being guessed by the controller. {@code created} is false
 * both for an existing live conversation and for one that was revived — from the client's perspective
 * the conversation was already there.
 */
public record CreateConversationResult(ConversationResponse conversation, boolean created) {
}
