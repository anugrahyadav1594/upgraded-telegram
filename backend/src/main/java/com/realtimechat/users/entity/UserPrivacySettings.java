package com.realtimechat.users.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-user privacy switches.
 *
 * <p>Defaults are deliberately "useful but controllable": chat is more usable when presence is
 * visible, so online status and last seen start enabled and one toggle away from hidden. Every
 * switch is enforced <b>server-side</b> before any data leaves the process — the frontend never
 * decides what may be revealed.
 *
 * <p>The invariant "hiding online status implies hiding last seen" is enforced both here and as a
 * database CHECK constraint, because half-hidden presence would still leak activity.
 */
@Entity
@Table(name = "user_privacy_settings")
public class UserPrivacySettings {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "show_online_status", nullable = false)
    private boolean showOnlineStatus = true;

    @Column(name = "show_last_seen", nullable = false)
    private boolean showLastSeen = true;

    @Column(name = "show_profile_in_search", nullable = false)
    private boolean showProfileInSearch = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "allow_messages_from", nullable = false)
    private MessagePolicy allowMessagesFrom = MessagePolicy.EVERYONE;

    @Column(name = "read_receipts", nullable = false)
    private boolean readReceipts = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserPrivacySettings() {
        // for JPA
    }

    public static UserPrivacySettings defaultsFor(UUID userId) {
        UserPrivacySettings settings = new UserPrivacySettings();
        settings.userId = userId;
        return settings;
    }

    /** Who is allowed to start a conversation with this user. */
    public enum MessagePolicy {
        /** Anyone who can find the profile may start a chat. */
        EVERYONE,
        /** Only users who already share a conversation (a mutual contact) may. */
        CONTACTS
    }

    // ------------------------------------------------------------------ behaviour

    /**
     * Applies a partial update. Hiding online status also hides last seen; the reverse is not
     * implied, so a user can show "online" without revealing their last activity.
     */
    public void update(boolean newShowOnlineStatus, boolean newShowLastSeen, boolean newShowProfileInSearch,
                       MessagePolicy newAllowMessagesFrom, boolean newReadReceipts) {
        this.showOnlineStatus = newShowOnlineStatus;
        this.showLastSeen = newShowOnlineStatus && newShowLastSeen;
        this.showProfileInSearch = newShowProfileInSearch;
        this.allowMessagesFrom = newAllowMessagesFrom;
        this.readReceipts = newReadReceipts;
    }

    public boolean presenceVisible() {
        return showOnlineStatus;
    }

    public boolean lastSeenVisible() {
        return showOnlineStatus && showLastSeen;
    }

    // ------------------------------------------------------------------ accessors

    public UUID getUserId() {
        return userId;
    }

    public boolean isShowOnlineStatus() {
        return showOnlineStatus;
    }

    public boolean isShowLastSeen() {
        return showLastSeen;
    }

    public boolean isShowProfileInSearch() {
        return showProfileInSearch;
    }

    public MessagePolicy getAllowMessagesFrom() {
        return allowMessagesFrom;
    }

    public boolean isReadReceipts() {
        return readReceipts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
