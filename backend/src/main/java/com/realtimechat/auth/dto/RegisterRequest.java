package com.realtimechat.auth.dto;

import com.realtimechat.common.validation.ValidDisplayName;
import com.realtimechat.common.validation.ValidUsername;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/auth/register} body.
 *
 * <p>Note what is <b>absent</b>: no {@code role}, no {@code emailVerified}, no {@code id}. Because
 * unknown JSON properties are rejected, attempting to send them is a 400 rather than a silently
 * ignored field.
 */
public record RegisterRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 254, message = "Email is too long")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 12, max = 128, message = "Password must be between 12 and 128 characters")
        String password,

        @NotBlank(message = "Username is required")
        @ValidUsername
        String username,

        @NotBlank(message = "Display name is required")
        @ValidDisplayName
        String displayName
) {
}
