package com.realtimechat.profiles.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * The pseudonymous public identity.
 *
 * <p>Contains no email address — by construction, not by convention. Any public API response is
 * projected from this entity, so exposing an email would require an explicit (and reviewable) join
 * to {@code users} in a DTO.
 *
 * <p>Username rules are enforced in three places: {@code UsernameRules} (friendly error),
 * the service (uniqueness, reserved words) and database CHECK/UNIQUE constraints (the invariant).
 */
@Entity
@Table(name = "profiles")
public class Profile {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "bio")
    private String bio;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "username_public", nullable = false)
    private boolean usernamePublic = true;

    /** Coarse presence hint: written on last disconnect, throttled to at most once per 5 minutes. */
    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "profile_completed_at")
    private Instant profileCompletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Profile() {
        // for JPA
    }

    public static Profile create(UUID userId, String username, String displayName) {
        Profile profile = new Profile();
        profile.userId = userId;
        profile.username = username;
        profile.displayName = displayName;
        profile.usernamePublic = true;
        profile.profileCompletedAt = Instant.now();
        return profile;
    }

    // ------------------------------------------------------------------ behaviour

    /** Updates the editable parts of the profile. Username changes are handled separately. */
    public void updateDetails(String displayName, String bio, String avatarUrl) {
        if (displayName != null) {
            this.displayName = displayName;
        }
        this.bio = bio;              // null clears the bio
        this.avatarUrl = avatarUrl;  // null clears the avatar
    }

    public void changeUsername(String newUsername) {
        this.username = newUsername;
    }

    public void setUsernamePublic(boolean usernamePublic) {
        this.usernamePublic = usernamePublic;
    }

    /** Records a last-seen value only when it is newer, so an out-of-order disconnect cannot rewind it. */
    public void touchLastSeen(Instant when) {
        if (lastSeenAt == null || when.isAfter(lastSeenAt)) {
            this.lastSeenAt = when;
        }
    }

    public boolean isComplete() {
        return profileCompletedAt != null && username != null && !username.isBlank();
    }

    // ------------------------------------------------------------------ accessors

    public UUID getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBio() {
        return bio;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public boolean isUsernamePublic() {
        return usernamePublic;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public Instant getProfileCompletedAt() {
        return profileCompletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
