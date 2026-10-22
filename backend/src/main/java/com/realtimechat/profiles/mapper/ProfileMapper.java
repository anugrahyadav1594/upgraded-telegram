package com.realtimechat.profiles.mapper;

import com.realtimechat.profiles.dto.ProfileSummary;
import com.realtimechat.profiles.dto.PublicProfileResponse;
import com.realtimechat.profiles.entity.Profile;
import com.realtimechat.users.service.ProfilePort;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Converts profile entities into public DTOs.
 *
 * <p>Mapping is explicit and one-directional on purpose: there is no reflection-based mapper that
 * could silently start serialising a new sensitive field. Adding {@code email} to
 * {@link PublicProfileResponse} would be a visible, reviewable change.
 */
@Component
public class ProfileMapper {

    /** Public view. Presence fields are supplied by the caller only when privacy allows it. */
    public PublicProfileResponse toPublicProfile(Profile profile,
                                                 boolean online,
                                                 Instant lastSeenAt,
                                                 String existingConversationId,
                                                 boolean blockedByMe,
                                                 boolean canMessage) {
        return new PublicProfileResponse(
                profile.getUserId(),
                profile.isUsernamePublic() ? profile.getUsername() : null,
                profile.isUsernamePublic(),
                profile.getDisplayName(),
                profile.getAvatarUrl(),
                profile.getBio(),
                online,
                lastSeenAt,
                profile.getCreatedAt(),
                existingConversationId,
                blockedByMe,
                canMessage);
    }

    /** Lightweight projection used inside conversation, message and notification payloads. */
    public ProfileSummary toSummary(Profile profile) {
        return new ProfileSummary(profile.getUserId(), profile.getUsername(), profile.getDisplayName(),
                profile.getAvatarUrl());
    }
}
