package com.realtimechat.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/auth/login} body.
 *
 * <p>{@code identifier} accepts either an email address or a username (with or without the leading
 * {@code @}) — one field, because asking a user to remember which one they signed up with is a
 * self-inflicted usability problem.
 */
public record LoginRequest(
        @NotBlank(message = "Email or username is required")
        @Size(max = 254, message = "Identifier is too long")
        String identifier,

        @NotBlank(message = "Password is required")
        @Size(max = 128, message = "Password is too long")
        String password
) {
}
