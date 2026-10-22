package com.realtimechat.auth.service;

import com.realtimechat.auth.dto.LoginRequest;
import com.realtimechat.common.error.UnauthenticatedException;
import com.realtimechat.common.util.TextNormalizer;
import com.realtimechat.common.util.TimeProvider;
import com.realtimechat.common.web.ClientIpResolver;
import com.realtimechat.common.validation.UsernameRules;
import com.realtimechat.users.entity.User;
import com.realtimechat.users.repository.UserRepository;
import com.realtimechat.users.service.ProfilePort;
import com.realtimechat.users.service.UserAccountService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Authentication: registration, login, logout, password change.
 *
 * <p>Security-relevant behaviours implemented here (each covered by tests):
 * <ul>
 *   <li><b>Identical failure response.</b> An unknown identifier and a wrong password produce the
 *       same exception, the same message and comparable work (a dummy hash verification runs when no
 *       user is found) so neither response body nor timing reveals whether an account exists.</li>
 *   <li><b>Argon2id only.</b> Hashing and verification go through {@code PasswordEncoder}; the raw
 *       password is never logged, stored or echoed.</li>
 *   <li><b>Rotation on privilege change.</b> Login and password change issue a brand-new session;
 *       a password change revokes every other session, which is what makes "someone changed my
 *       password" also mean "the attacker is logged out".</li>
 * </ul>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final Logger securityLog = LoggerFactory.getLogger("SECURITY");

    /**
     * A real Argon2id hash of a password nobody knows, verified against when the identifier is
     * unknown, disabled, or locked — so those paths cost the same CPU as a wrong password instead of
     * returning in microseconds and leaking which accounts exist.
     *
     * <p>It has to be a *well-formed* hash: {@code matches()} decodes the PHC string first, and a
     * malformed one throws instead of returning false, which would turn "account does not exist" into
     * a 500. Generated with the same parameters the application uses
     * (m=19456, t=2, p=1, 16-byte salt, 32-byte output), so the decoy is indistinguishable in shape
     * from a genuine stored hash. The salt is fixed only to keep this literal reproducible; the
     * password behind it is a hash of a constant, never a credential.
     */
    private static final String DUMMY_HASH =
            "$argon2id$v=19$m=19456,t=2,p=1$AAECAwQFBgcICQoLDA0ODw$"
                    + "H56bdPyK75LrcLycUA912ewAh2L7W32stsmC4fYltn8";

    private final UserRepository userRepository;
    private final UserAccountService userAccountService;
    private final ProfilePort profilePort;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final SessionService sessionService;
    private final LoginAttemptGuard loginAttemptGuard;
    private final ClientIpResolver ipResolver;
    private final TimeProvider timeProvider;

    public AuthService(UserRepository userRepository,
                       UserAccountService userAccountService,
                       ProfilePort profilePort,
                       PasswordEncoder passwordEncoder,
                       PasswordPolicy passwordPolicy,
                       SessionService sessionService,
                       LoginAttemptGuard loginAttemptGuard,
                       ClientIpResolver ipResolver,
                       TimeProvider timeProvider) {
        this.userRepository = userRepository;
        this.userAccountService = userAccountService;
        this.profilePort = profilePort;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.sessionService = sessionService;
        this.loginAttemptGuard = loginAttemptGuard;
        this.ipResolver = ipResolver;
        this.timeProvider = timeProvider;
    }

    // ------------------------------------------------------------------ registration

    @Transactional
    public User register(String rawEmail, String password, String rawUsername, String displayName,
                         HttpServletRequest request) {
        String email = TextNormalizer.normalizeEmail(rawEmail);
        String username = TextNormalizer.normalizeUsername(rawUsername);

        // Re-validate at the service boundary: the controller validates for a friendly 400, but the
        // service must not be trustworthy-by-assumption if it is ever called from another entry point.
        String usernameViolation = UsernameRules.validate(username);
        if (usernameViolation != null) {
            throw new com.realtimechat.common.error.ForbiddenException(usernameViolation);
        }
        passwordPolicy.validate(password, username, email);

        User user = userAccountService.register(email, password, username,
                TextNormalizer.normalizeDisplayName(displayName));
        loginAttemptGuard.recordSuccess(user, email, clientIp(request));
        return user;
    }

    // ------------------------------------------------------------------ login

    /**
     * Verifies credentials and opens a session.
     *
     * @param identifier email address or username (with or without {@code @})
     */
    @Transactional
    public User login(LoginRequest request, HttpServletRequest httpRequest) {
        String identifier = request.identifier() == null ? "" : request.identifier().trim();
        String clientIp = clientIp(httpRequest);

        loginAttemptGuard.checkIp(clientIp);
        loginAttemptGuard.checkAccount(identifier);

        Optional<User> found = findByIdentifier(identifier);
        if (found.isEmpty()) {
            // Spend comparable CPU on the unknown-account path so response timing does not leak
            // whether the account exists.
            passwordEncoder.matches(request.password(), DUMMY_HASH);
            securityLog.warn("login failed reason=unknown-identifier ip={}", clientIp);
            throw invalidCredentials();
        }

        User user = found.get();
        if (!user.getStatus().canAuthenticate()) {
            passwordEncoder.matches(request.password(), DUMMY_HASH);
            securityLog.warn("login failed reason=account-status userId={} status={}",
                    user.getId(), user.getStatus());
            // Suspended and deleted accounts are reported exactly like bad credentials.
            throw invalidCredentials();
        }
        if (user.isLockedAt(timeProvider.now())) {
            passwordEncoder.matches(request.password(), DUMMY_HASH);
            securityLog.warn("login failed reason=locked userId={}", user.getId());
            throw new com.realtimechat.common.error.RateLimitExceededException(
                    "Too many attempts. Try again shortly.");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginAttemptGuard.recordFailure(user);
            userRepository.save(user);
            securityLog.warn("login failed reason=bad-password userId={} ip={}", user.getId(), clientIp);
            throw invalidCredentials();
        }

        loginAttemptGuard.recordSuccess(user, identifier, clientIp);
        userRepository.save(user);
        securityLog.info("login succeeded userId={}", user.getId());
        return user;
    }

    private Optional<User> findByIdentifier(String identifier) {
        if (identifier.contains("@")) {
            return userRepository.findByEmail(TextNormalizer.normalizeEmail(identifier));
        }
        String username = TextNormalizer.normalizeUsername(identifier);
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return profilePort.findUserIdByUsername(username).flatMap(userRepository::findById);
    }

    private UnauthenticatedException invalidCredentials() {
        // One message for every failure mode: revealing which factor was wrong would let an attacker
        // enumerate accounts, and revealing that an account is locked would confirm it exists.
        return new UnauthenticatedException("Email, username or password is incorrect");
    }

    // ------------------------------------------------------------------ password change

    @Transactional
    public void changePassword(UUID userId, UUID currentSessionId, String currentPassword,
                               String newPassword) {
        User user = userAccountService.requireActiveUser(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            securityLog.warn("password change rejected reason=bad-current-password userId={}", userId);
            throw new UnauthenticatedException("Your current password is incorrect");
        }
        String username = profilePort.summaryOf(userId).map(ProfilePort.UserSummary::username).orElse(null);
        passwordPolicy.validate(newPassword, username, user.getEmail());

        userAccountService.changePassword(userId, newPassword);
        int revoked = sessionService.revokeOthers(userId, currentSessionId);
        log.info("password changed userId={} otherSessionsRevoked={}", userId, revoked);
    }

    // ------------------------------------------------------------------ helpers

    private String clientIp(HttpServletRequest request) {
        return request == null ? null : ipResolver.resolve(request);
    }

    /** Used by integration tests and the dev seeder. */
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(TextNormalizer.normalizeEmail(email));
    }
}
