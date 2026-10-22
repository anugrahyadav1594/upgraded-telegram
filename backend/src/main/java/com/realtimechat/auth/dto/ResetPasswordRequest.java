package com.realtimechat.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /api/v1/auth/reset-password} body: the emailed token plus the new password. */
public record ResetPasswordRequest(
        @NotBlank(message = "Reset token is required")
        @Size(max = 512)
        String token,

        @NotBlank(message = "New password is required")
        @Size(min = 12, max = 128, message = "Password must be between 12 and 128 characters")
        String newPassword
) {
}
