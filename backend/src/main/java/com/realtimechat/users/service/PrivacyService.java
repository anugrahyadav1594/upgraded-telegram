package com.realtimechat.users.service;

import com.realtimechat.common.error.PrivacyRestrictedException;
import com.realtimechat.users.entity.UserPrivacySettings;
import com.realtimechat.users.repository.UserPrivacySettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Privacy settings: reading, updating and — most importantly — <b>deciding</b>.
 *
 * <p>Every questionable decision (may this user be found? may their presence be revealed? may this
 * person start a conversation?) is answered here, so the answer is the same no matter which feature
 * asks. A missing settings row is treated as safe defaults, never as "no restrictions".
 */
@Service
public class PrivacyService {

    private static final Logger log = LoggerFactory.getLogger(PrivacyService.class);

    private final UserPrivacySettingsRepository repository;

    public PrivacyService(UserPrivacySettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public UserPrivacySettings getOrDefault(UUID userId) {
        return repository.findById(userId).orElseGet(() -> UserPrivacySettings.defaultsFor(userId));
    }

    @Transactional(readOnly = true)
    public Map<UUID, UserPrivacySettings> getMany(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, UserPrivacySettings> found = repository.findAllByUserIdIn(userIds).stream()
                .collect(Collectors.toMap(UserPrivacySettings::getUserId, Function.identity()));
        // Defaults for users without a row: absence of configuration must not mean "public".
        for (UUID userId : userIds) {
            found.putIfAbsent(userId, UserPrivacySettings.defaultsFor(userId));
        }
        return found;
    }

    @Transactional
    public UserPrivacySettings update(UUID userId,
                                      boolean showOnlineStatus,
                                      boolean showLastSeen,
                                      boolean showProfileInSearch,
                                      UserPrivacySettings.MessagePolicy allowMessagesFrom,
                                      boolean readReceipts) {
        UserPrivacySettings settings = repository.findById(userId)
                .orElseGet(() -> UserPrivacySettings.defaultsFor(userId));
        settings.update(showOnlineStatus, showLastSeen, showProfileInSearch, allowMessagesFrom, readReceipts);
        UserPrivacySettings saved = repository.save(settings);
        log.info("privacy updated userId={} online={} lastSeen={} searchable={} messages={} receipts={}",
                userId, saved.isShowOnlineStatus(), saved.isShowLastSeen(), saved.isShowProfileInSearch(),
                saved.getAllowMessagesFrom(), saved.isReadReceipts());
        return saved;
    }

    // ------------------------------------------------------------------ decisions

    /** May other users see this user in search results? */
    @Transactional(readOnly = true)
    public boolean isSearchable(UUID userId) {
        return getOrDefault(userId).isShowProfileInSearch();
    }

    /** May the requester see this user's online status? */
    @Transactional(readOnly = true)
    public boolean presenceVisible(UUID targetUserId) {
        return getOrDefault(targetUserId).presenceVisible();
    }

    /** May the requester see this user's last-seen timestamp? */
    @Transactional(readOnly = true)
    public boolean lastSeenVisible(UUID targetUserId) {
        return getOrDefault(targetUserId).lastSeenVisible();
    }

    /** Does this user broadcast read receipts? */
    @Transactional(readOnly = true)
    public boolean readReceiptsEnabled(UUID userId) {
        return getOrDefault(userId).isReadReceipts();
    }

    /**
     * Enforces {@code allowMessagesFrom} for a <b>new</b> conversation.
     *
     * @param targetUserId      the user who would receive the message
     * @param requesterId       the user trying to start the conversation
     * @param alreadyInContact  whether the two already share a conversation
     * @throws PrivacyRestrictedException when the target only accepts messages from contacts
     */
    @Transactional(readOnly = true)
    public void requireMayStartConversation(UUID targetUserId, UUID requesterId, boolean alreadyInContact) {
        UserPrivacySettings settings = getOrDefault(targetUserId);
        if (settings.getAllowMessagesFrom() == UserPrivacySettings.MessagePolicy.CONTACTS && !alreadyInContact) {
            throw new PrivacyRestrictedException(
                    "This person only accepts messages from people they already chat with");
        }
    }

    public Optional<UserPrivacySettings> find(UUID userId) {
        return repository.findById(userId);
    }
}
