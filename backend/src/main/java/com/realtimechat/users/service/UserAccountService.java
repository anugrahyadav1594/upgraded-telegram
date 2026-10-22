package com.realtimechat.users.service;

import com.realtimechat.common.error.ConflictException;
import com.realtimechat.common.error.EmailTakenException;
import com.realtimechat.common.error.NotFoundException;
import com.realtimechat.common.error.UsernameTakenException;
import com.realtimechat.common.util.TextNormalizer;
import com.realtimechat.common.util.TimeProvider;
import com.realtimechat.users.entity.User;
import com.realtimechat.users.entity.UserPrivacySettings;
import com.realtimechat.users.entity.UserStatus;
import com.realtimechat.users.repository.UserBlockRepository;
import com.realtimechat.users.repository.UserPrivacySettingsRepository;
import com.realtimechat.users.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Account lifecycle: creation, credential changes and deletion.
 *
 * <p>Registration is one transaction that produces a consistent account: {@code users} +
 * {@code profiles} (via {@link ProfilePort}) + privacy defaults + notification defaults. A partial
 * account — a user who cannot be found by username, or who has no privacy row — is therefore not
 * representable.
 */
@Service
public class UserAccountService {

    private static final Logger log = LoggerFactory.getLogger(UserAccountService.class);
    private static final Logger securityLog = LoggerFactory.getLogger("SECURITY");

    private final UserRepository userRepository;
    private final UserPrivacySettingsRepository privacyRepository;
    private final UserBlockRepository blockRepository;
    private final ProfilePort profilePort;
    private final PasswordEncoder passwordEncoder;
    private final TimeProvider timeProvider;

    public UserAccountService(UserRepository userRepository,
                              UserPrivacySettingsRepository privacyRepository,
                              UserBlockRepository blockRepository,
                              ProfilePort profilePort,
                              PasswordEncoder passwordEncoder,
                              TimeProvider timeProvider) {
        this.userRepository = userRepository;
        this.privacyRepository = privacyRepository;
        this.blockRepository = blockRepository;
        this.profilePort = profilePort;
        this.passwordEncoder = passwordEncoder;
        this.timeProvider = timeProvider;
    }

    /**
     * Creates an account together with its pseudonymous profile and default settings.
     *
     * @param normalizedEmail lowercased email (normalisation happens in the controller/service above)
     * @param rawPassword     the plaintext password, hashed here so it never travels further
     * @throws ConflictException when the email or username is already taken
     */
    @Transactional
    public User register(String normalizedEmail, String rawPassword, String username, String displayName) {
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailTakenException("That email address is already registered");
        }
        if (profilePort.usernameExists(username)) {
            throw new UsernameTakenException("That username is already taken");
        }

        User user = User.create(normalizedEmail, passwordEncoder.encode(rawPassword));
        userRepository.save(user);
        profilePort.createProfile(user.getId(), username, displayName);
        privacyRepository.save(UserPrivacySettings.defaultsFor(user.getId()));

        securityLog.info("account registered userId={}", user.getId());
        return user;
    }

    /** Requires an existing, non-deleted account. Used by every authenticated request path. */
    @Transactional(readOnly = true)
    public User requireActiveUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Account not found"));
        if (user.getStatus() == UserStatus.DELETED) {
            throw new NotFoundException("Account not found");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public User requireById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Account not found"));
    }

    /**
     * Changes the password after the caller has verified the current one.
     *
     * @return the updated user (the caller revokes sessions)
     */
    @Transactional
    public User changePassword(UUID userId, String newRawPassword) {
        User user = requireActiveUser(userId);
        user.changePassword(passwordEncoder.encode(newRawPassword));
        securityLog.info("password changed userId={}", userId);
        return user;
    }

    /**
     * Deletes an account: personal data is erased, authored content is anonymised onto the
     * tombstone identity so conversations and reply threads stay coherent for the other party.
     *
     * <p>Hard-deleting the messages would silently rewrite history for the remaining participant
     * (a gap in the middle of a thread), which is why the row is detached from the person rather
     * than removed.
     */
    @Transactional
    public void deleteAccount(UUID userId) {
        User user = requireActiveUser(userId);
        if (user.isTombstone()) {
            throw new ConflictException("Cannot delete the tombstone account");
        }
        blockRepository.deleteAllInvolving(java.util.List.of(userId));
        profilePort.deleteProfile(userId);
        boolean locked = user.isLockedAt(timeProvider.now());
        user.anonymiseForDeletion(timeProvider.now());
        securityLog.info("account deleted userId={} previouslyLocked={}", userId, locked);
    }

    /** Convenience for tests and the dev seeder; production code uses the transactional variants. */
    @Transactional(readOnly = true)
    public User findByEmail(String email) {
        return userRepository.findByEmail(TextNormalizer.normalizeEmail(email)).orElse(null);
    }
}
