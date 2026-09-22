package com.realtimechat.auth.dto;

/**
 * Generic informational response for endpoints whose only result is a message
 * (for example {@code POST /auth/forgot-password}).
 */
public record MessageResponse(String message) {

    public static MessageResponse of(String message) {
        return new MessageResponse(message);
    }
}
