package com.realtimechat.users.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * The caller's own account view — the only DTO that contains an email address.
 *
 * <p>It is returned exclusively by endpoints that operate on the authenticated user
 * ({@code /auth/me}, {@code /auth/register}, {@code /auth/login}, {@code /users/me}). Because the
 * type is distinct from {@code PublicProfileResponse}, a public endpoint cannot accidentally return
 * an email: it would have to change its return type.
 */
public record AccountResponse(
        UUID id,
        String email,
        String username,
        String displayName,
        String avatarUrl,
        String bio,
        boolean usernamePublic,
        boolean emailVerified,
        boolean profileComplete,
        PrivacySettingsResponse privacy,
        long unreadNotifications,
        Instant createdAt,
        Instant lastLoginAt
) {
}
