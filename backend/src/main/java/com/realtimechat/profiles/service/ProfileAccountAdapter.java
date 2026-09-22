package com.realtimechat.profiles.service;

import com.realtimechat.profiles.dto.ProfileSummary;
import com.realtimechat.profiles.entity.Profile;
import com.realtimechat.profiles.mapper.ProfileMapper;
import com.realtimechat.profiles.repository.ProfileRepository;
import com.realtimechat.users.service.ProfilePort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implements the {@link ProfilePort} port declared by the {@code users} module.
 *
 * <p>This is the only way account code touches profiles, which keeps the dependency direction
 * (auth/users → profiles) one-way and makes both modules extractable later: the port is a contract,
 * and it is implemented by whichever service owns profiles.
 *
 * <p>All methods run inside the caller's transaction, so "create account + create profile" either
 * both happen or neither does.
 */
@Service
public class ProfileAccountAdapter implements ProfilePort {

    private final ProfileRepository profileRepository;
    private final ProfileMapper profileMapper;

    public ProfileAccountAdapter(ProfileRepository profileRepository, ProfileMapper profileMapper) {
        this.profileRepository = profileRepository;
        this.profileMapper = profileMapper;
    }

    @Override
    @Transactional
    public void createProfile(UUID userId, String username, String displayName) {
        try {
            profileRepository.save(Profile.create(userId, username, displayName));
        } catch (DataIntegrityViolationException ex) {
            // Unique violation on username: translate the database guarantee into the domain error
            // the API documents (409 USERNAME_TAKEN) rather than letting it surface as a 500.
            throw new com.realtimechat.common.error.UsernameTakenException("That username is already taken");
        }
    }

    @Override
    @Transactional
    public void deleteProfile(UUID userId) {
        profileRepository.deleteById(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean usernameExists(String normalizedUsername) {
        return profileRepository.existsByUsername(normalizedUsername);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findUserIdByUsername(String normalizedUsername) {
        return profileRepository.findByUsername(normalizedUsername).map(Profile::getUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, UserSummary> summariesOf(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<Profile> profiles = profileRepository.findAllByUserIdIn(userIds);
        return profiles.stream()
                .map(this::toSummary)
                .collect(Collectors.toMap(UserSummary::userId, Function.identity()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserSummary> summaryOf(UUID userId) {
        return profileRepository.findById(userId).map(this::toSummary);
    }

    private UserSummary toSummary(Profile profile) {
        return new UserSummary(profile.getUserId(), profile.getUsername(), profile.getDisplayName(),
                profile.getAvatarUrl(), profile.getBio(), profile.isUsernamePublic());
    }

    /** Kept for symmetry with the mapper; not part of the port surface. */
    ProfileSummary toDto(Profile profile) {
        return profileMapper.toSummary(profile);
    }
}
