package com.realtimechat.profiles.dto;

import com.realtimechat.common.validation.ValidBio;
import com.realtimechat.common.validation.ValidDisplayName;
import com.realtimechat.common.validation.ValidUsername;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code PATCH /api/v1/users/me} body. All fields optional: absent means "leave unchanged".
 *
 * <p>Username changes are rate limited (once per 7 days) because a handle is an identity: frequent
 * changes make it impossible for other users to know who they are talking to.
 */
public record UpdateProfileRequest(
        @ValidDisplayName String displayName,
        @ValidBio String bio,
        @Pattern(regexp = "^https://[\\w\\-./%?=&#:+~]{1,500}$",
                message = "Avatar must be an https URL")
        @Size(max = 512)
        String avatarUrl,
        @ValidUsername String username,
        Boolean usernamePublic
) {
}
