package com.realtimechat.conversations.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * {@code POST /api/v1/conversations} body.
 *
 * <p>Only a peer id is accepted: the creator is the authenticated caller, never a body field. A
 * request that tries to specify the other participant as well would be a 400 (unknown properties are
 * rejected), which is the intended failure mode.
 */
public record CreateConversationRequest(
        @NotNull(message = "peerUserId is required")
        UUID peerUserId
) {
}
