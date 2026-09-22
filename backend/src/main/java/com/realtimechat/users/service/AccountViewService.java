package com.realtimechat.users.service;

import com.realtimechat.common.error.NotFoundException;
import com.realtimechat.users.dto.AccountResponse;
import com.realtimechat.users.dto.PrivacySettingsResponse;
import com.realtimechat.users.entity.User;
import com.realtimechat.users.entity.UserPrivacySettings;
import com.realtimechat.users.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Composes the caller's own account view.
 *
 * <p>This is the "BFF" of the identity modules: it joins account data (email, role, verification),
 * profile data (username, display name, avatar, bio) and settings into the single object the
 * frontend needs on start-up. Keeping the composition here — rather than in a controller — means the
 * same shape is returned by every endpoint that reports "who am I".
 */
@Service
public class AccountViewService {

    private final UserRepository userRepository;
    private final ProfilePort profilePort;
    private final PrivacyService privacyService;
    private final NotificationCountPort notificationCountPort;

    public AccountViewService(UserRepository userRepository,
                              ProfilePort profilePort,
                              PrivacyService privacyService,
                              NotificationCountPort notificationCountPort) {
        this.userRepository = userRepository;
        this.profilePort = profilePort;
        this.privacyService = privacyService;
        this.notificationCountPort = notificationCountPort;
    }

    @Transactional(readOnly = true)
    public AccountResponse forUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Account not found"));
        ProfilePort.UserSummary summary = profilePort.summaryOf(userId)
                .orElse(new ProfilePort.UserSummary(userId, "", "", null));

        UserPrivacySettings settings = privacyService.getOrDefault(userId);
        return new AccountResponse(
                user.getId(),
                user.getEmail(),
                summary.username(),
                summary.displayName(),
                summary.avatarUrl(),
                summary.bio(),
                summary.usernamePublic(),
                user.isEmailVerified(),
                !summary.username().isBlank(),
                PrivacySettingsResponse.from(settings),
                notificationCountPort.unreadCount(userId),
                user.getCreatedAt(),
                user.getLastLoginAt());
    }

    /**
     * Overload used by auth flows that already hold the entity (avoids a redundant read right after
     * a successful login or registration).
     */
    @Transactional(readOnly = true)
    public AccountResponse forUser(User user) {
        return forUser(user.getId());
    }
}
