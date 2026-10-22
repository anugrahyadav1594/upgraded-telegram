package com.realtimechat.conversations.dto;

/**
 * {@code PATCH /api/v1/conversations/{id}} body.
 *
 * <p>Both fields are optional (partial update); {@code null} means "leave unchanged". Booleans are
 * boxed on purpose so that {@code {"muted": false}} is distinguishable from an absent field.
 */
public record UpdateConversationRequest(Boolean muted, Boolean archived) {

    public boolean isEmpty() {
        return muted == null && archived == null;
    }
}
