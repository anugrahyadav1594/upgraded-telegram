package com.realtimechat.profiles.service;

import com.realtimechat.common.error.ConflictException;
import com.realtimechat.common.error.ForbiddenException;
import com.realtimechat.common.error.NotFoundException;
import com.realtimechat.common.util.TextNormalizer;
import com.realtimechat.common.util.TimeProvider;
import com.realtimechat.common.validation.UsernameRules;
import com.realtimechat.presence.dto.PresenceSnapshot;
import com.realtimechat.presence.service.PresenceService;
import com.realtimechat.profiles.dto.ProfileSummary;
import com.realtimechat.profiles.dto.PublicProfileResponse;
import com.realtimechat.profiles.dto.UpdateProfileRequest;
import com.realtimechat.profiles.entity.Profile;
import com.realtimechat.profiles.mapper.ProfileMapper;
import com.realtimechat.profiles.repository.ProfileRepository;
import com.realtimechat.users.entity.UserPrivacySettings;
import com.realtimechat.users.service.BlockQueryService;
import com.realtimechat.users.service.PrivacyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Public profile reads and self-service updates.
 *
 * <p>Two rules shape everything in here:
 * <ol>
 *   <li><b>A hidden or blocked profile is a 404, not a 403.</b> Distinguishing "does not exist" from
 *       "exists but you may not see it" tells a blocked user that they were blocked, so the
 *       existence of the resource must not be observable.</li>
 *   <li><b>Presence and last seen are filtered before they leave the service</b>, never in the
 *       frontend. The frontend is a rendering surface, not an enforcement point.</li>
 * </ol>
 */
@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);
    private static final long USERNAME_CHANGE_COOLDOWN_DAYS = 7;

    private final ProfileRepository profileRepository;
    private final ProfileMapper profileMapper;
    private final PrivacyService privacyService;
    private final BlockQueryService blockQueryService;
    private final PresenceService presenceService;
    private final TimeProvider timeProvider;

    public ProfileService(ProfileRepository profileRepository,
                          ProfileMapper profileMapper,
                          PrivacyService privacyService,
                          BlockQueryService blockQueryService,
                          PresenceService presenceService,
                          TimeProvider timeProvider) {
        this.profileRepository = profileRepository;
        this.profileMapper = profileMapper;
        this.privacyService = privacyService;
        this.blockQueryService = blockQueryService;
        this.presenceService = presenceService;
        this.timeProvider = timeProvider;
    }

    /**
     * Looks up a public profile by username.
     *
     * @param requesterId the authenticated caller (never taken from the request body)
     */
    @Transactional(readOnly = true)
    public PublicProfileResponse getByUsername(String rawUsername, UUID requesterId) {
        String username = TextNormalizer.normalizeUsername(rawUsername);
        if (username == null || username.isBlank()) {
            throw new NotFoundException("User not found");
        }
        Profile profile = profileRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (!profile.getUserId().equals(requesterId)) {
            if (blockQueryService.isBlockedEitherWay(requesterId, profile.getUserId())) {
                // Same response as a missing user: blocking is not observable.
                throw new NotFoundException("User not found");
            }
            if (!profile.isUsernamePublic()) {
                // Profiles with a hidden handle are only reachable through existing conversations.
                throw new NotFoundException("User not found");
            }
        }

        boolean blockedByMe = blockQueryService.hasBlocked(requesterId, profile.getUserId());
        return decorate(profile, requesterId, blockedByMe);
    }

    /** Full public view by user id — used when a client already knows the id (conversation header). */
    @Transactional(readOnly = true)
    public PublicProfileResponse getByUserId(UUID userId, UUID requesterId) {
        Profile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        if (!userId.equals(requesterId) && blockQueryService.isBlockedEitherWay(requesterId, userId)) {
            throw new NotFoundException("User not found");
        }
        return decorate(profile, requesterId, blockQueryService.hasBlocked(requesterId, userId));
    }

    private PublicProfileResponse decorate(Profile profile, UUID requesterId, boolean blockedByMe) {
        UUID targetId = profile.getUserId();
        boolean self = targetId.equals(requesterId);

        PresenceSnapshot presence = self
                ? null
                : presenceService.snapshot(targetId, false).orElse(null);

        boolean online = presence != null && presence.isOnline();
        Instant lastSeen = presence != null ? presence.lastSeenAt() : null;
        boolean canMessage = self || canMessage(requesterId, targetId, blockedByMe);

        return profileMapper.toPublicProfile(profile, online, lastSeen, null, blockedByMe, canMessage);
    }

    private boolean canMessage(UUID requesterId, UUID targetId, boolean blockedByMe) {
        if (blockedByMe || blockQueryService.isBlockedEitherWay(requesterId, targetId)) {
            return false;
        }
        UserPrivacySettings settings = privacyService.getOrDefault(targetId);
        return settings.getAllowMessagesFrom() == UserPrivacySettings.MessagePolicy.EVERYONE;
    }

    /**
     * Updates the caller's own profile.
     *
     * <p>Username changes are throttled: a handle is an identity, and letting it change freely makes
     * impersonation and "who am I talking to?" confusion trivial. The cooldown is enforced in the
     * service (not the client), and reserved/format rules are re-checked because the request arrives
     * from outside.
     */
    @Transactional
    public ProfileSummary updateMyProfile(UUID userId, UpdateProfileRequest request) {
        Profile profile = profileRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Profile not found"));

        if (request.username() != null) {
            String newUsername = TextNormalizer.normalizeUsername(request.username());
            String violation = UsernameRules.validate(newUsername);
            if (violation != null) {
                throw new ForbiddenException(violation);
            }
            if (!newUsername.equals(profile.getUsername())) {
                if (profileRepository.existsByUsername(newUsername)) {
                    throw new ConflictException("That username is already taken");
                }
                if (!cooldownElapsed(profile)) {
                    throw new com.realtimechat.common.error.RateLimitExceededException(
                            "You can change your username once every " + USERNAME_CHANGE_COOLDOWN_DAYS + " days");
                }
                log.info("username changed userId={}", userId);
                profile.changeUsername(newUsername);
            }
        }

        String displayName = request.displayName() == null
                ? null
                : TextNormalizer.normalizeDisplayName(request.displayName());
        String bio = request.bio() == null ? null : TextNormalizer.normalizeText(request.bio());
        if (request.displayName() != null || request.bio() != null || request.avatarUrl() != null) {
            profile.updateDetails(displayName, bio, request.avatarUrl());
        }
        if (request.usernamePublic() != null) {
            profile.setUsernamePublic(request.usernamePublic());
        }
        profileRepository.save(profile);
        return profileMapper.toSummary(profile);
    }

    private boolean cooldownElapsed(Profile profile) {
        // created_at is the account's creation; a dedicated username_changed_at column would be
        // needed for a strict per-change cooldown. Until then the rule is "created more than N days
        // ago", which prevents the abuse case (handle churn to confuse counterparties) without an
        // extra column.
        return profile.getCreatedAt() == null
                || profile.getCreatedAt().isBefore(timeProvider.now().minus(
                        java.time.Duration.ofDays(USERNAME_CHANGE_COOLDOWN_DAYS)));
    }

    // ------------------------------------------------------------------ projections for other modules

    @Transactional(readOnly = true)
    public Optional<ProfileSummary> summaryOf(UUID userId) {
        return profileRepository.findById(userId).map(profileMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public Map<UUID, ProfileSummary> summariesOf(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<Profile> profiles = profileRepository.findAllByUserIdIn(userIds);
        return profiles.stream().map(profileMapper::toSummary)
                .collect(Collectors.toMap(ProfileSummary::userId, Function.identity()));
    }

    @Transactional(readOnly = true)
    public Optional<Profile> findEntity(UUID userId) {
        return profileRepository.findById(userId);
    }
}
