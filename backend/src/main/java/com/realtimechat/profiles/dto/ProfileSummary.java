package com.realtimechat.profiles.dto;

import java.util.UUID;

/**
 * Minimal public representation of a user, embedded in other payloads (conversation peers, message
 * senders, notification actors). Never contains an email address or any private field.
 */
public record ProfileSummary(
        UUID userId,
        String username,
        String displayName,
        String avatarUrl
) {
}
