package com.realtimechat.users.service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Dependency inversion port for the public (pseudonymous) profile.
 *
 * <p>Declared in the {@code users} module and implemented by {@code profiles}. The account module
 * needs to create/delete profiles and resolve {@code @username → userId} at login, but it must not
 * depend on the profiles module's internals — otherwise account and profile code could not be
 * separated later, and the module graph would grow a cycle (profiles already depends on users).
 *
 * <p>Implementations must be transactional callers' participants: these methods are always invoked
 * inside the caller's transaction so that "account created + profile created" is atomic.
 */
public interface ProfilePort {

    /** Creates the profile row for a freshly registered account. */
    void createProfile(UUID userId, String username, String displayName);

    /** Removes the profile row when an account is deleted (hard delete; the data is personal). */
    void deleteProfile(UUID userId);

    boolean usernameExists(String normalizedUsername);

    Optional<UUID> findUserIdByUsername(String normalizedUsername);

    /** Batch summary lookup used to enrich conversation and notification payloads. */
    Map<UUID, UserSummary> summariesOf(Collection<UUID> userIds);

    Optional<UserSummary> summaryOf(UUID userId);

    /** Minimal public projection of another user; never contains an email address. */
    record UserSummary(UUID userId, String username, String displayName, String avatarUrl, String bio,
                       boolean usernamePublic) {

        /** Convenience for callers that only need the identifying fields. */
        public UserSummary(UUID userId, String username, String displayName, String avatarUrl) {
            this(userId, username, displayName, avatarUrl, null, true);
        }
    }
}
