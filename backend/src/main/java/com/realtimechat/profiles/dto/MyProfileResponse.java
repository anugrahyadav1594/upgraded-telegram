package com.realtimechat.profiles.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * The caller's own profile as returned by profile-related endpoints.
 *
 * <p>Distinct from {@link PublicProfileResponse} because the owner sees their handle even when it is
 * hidden from others, and previews the exact public view they present.
 */
public record MyProfileResponse(
        UUID userId,
        String username,
        String displayName,
        String bio,
        String avatarUrl,
        boolean usernamePublic,
        Instant memberSince,
        Instant updatedAt
) {
}
