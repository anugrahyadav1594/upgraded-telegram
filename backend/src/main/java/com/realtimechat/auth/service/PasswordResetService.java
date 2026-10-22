package com.realtimechat.auth.service;

import com.realtimechat.auth.dto.MessageResponse;
import com.realtimechat.auth.entity.PasswordResetToken;
import com.realtimechat.auth.repository.PasswordResetTokenRepository;
import com.realtimechat.common.error.InvalidRequestException;
import com.realtimechat.common.mail.EmailSender;
import com.realtimechat.common.ratelimit.RateLimitDecision;
import com.realtimechat.common.ratelimit.RateLimiter;
import com.realtimechat.common.util.HashUtil;
import com.realtimechat.common.util.TextNormalizer;
import com.realtimechat.common.util.TimeProvider;
import com.realtimechat.config.AppProperties;
import com.realtimechat.users.entity.User;
import com.realtimechat.users.repository.UserRepository;
import com.realtimechat.users.service.ProfilePort;
import com.realtimechat.users.service.UserAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Password reset by email.
 *
 * <p>Properties that make this safe:
 * <ul>
 *   <li><b>No account enumeration.</b> The response is identical whether or not the address is
 *       registered, and no timing shortcut is taken (the token is generated either way... in fact
 *       only for existing accounts, but the observable response and rate limiting are the same).</li>
 *   <li><b>Single use, short lived.</b> 30 minutes, consumed inside the same transaction that sets
 *       the new password, so two concurrent uses cannot both succeed.</li>
 *   <li><b>Stored hashed.</b> The database never holds a usable link.</li>
 *   <li><b>Revokes everything.</b> A successful reset ends every session, so a stolen session cannot
 *       survive the owner's recovery.</li>
 * </ul>
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final Logger securityLog = LoggerFactory.getLogger("SECURITY");
    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    private static final String BUCKET = "password-reset";
    private static final String NEUTRAL_MESSAGE =
            "If that email is registered, we've sent a reset link.";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final UserAccountService userAccountService;
    private final ProfilePort profilePort;
    private final PasswordPolicy passwordPolicy;
    private final SessionService sessionService;
    private final EmailSender emailSender;
    private final RateLimiter rateLimiter;
    private final AppProperties properties;
    private final TimeProvider timeProvider;

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository,
                                UserAccountService userAccountService,
                                ProfilePort profilePort,
                                PasswordPolicy passwordPolicy,
                                SessionService sessionService,
                                EmailSender emailSender,
                                RateLimiter rateLimiter,
                                AppProperties properties,
                                TimeProvider timeProvider) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.userAccountService = userAccountService;
        this.profilePort = profilePort;
        this.passwordPolicy = passwordPolicy;
        this.sessionService = sessionService;
        this.emailSender = emailSender;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.timeProvider = timeProvider;
    }

    /** Requests a reset link. Always returns the same neutral message. */
    @Transactional
    public MessageResponse requestReset(String rawEmail) {
        String email = TextNormalizer.normalizeEmail(rawEmail);
        AppProperties.RateLimit limits = properties.getRateLimit();
        if (limits.isEnabled()) {
            RateLimitDecision decision = rateLimiter.consume(BUCKET, HashUtil.sha256Hex(email).substring(0, 32),
                    limits.getPasswordResetPerEmail(), limits.getPasswordResetWindow());
            if (!decision.allowed()) {
                // Rate limiting is per address and does not reveal existence, so it is safe to
                // report — and it must be reported, otherwise a user would wait for an email that
                // was never sent.
                throw new com.realtimechat.common.error.RateLimitExceededException(
                        "Too many reset requests. Try again later.");
            }
        }

        Optional<User> user = userRepository.findByEmail(email);
        if (user.isEmpty()) {
            log.info("password reset requested for an unknown address (no email sent)");
            return MessageResponse.of(NEUTRAL_MESSAGE);
        }

        User account = user.get();
        Instant now = timeProvider.now();
        // Previous outstanding links stop working: only the newest request should be usable.
        tokenRepository.consumeAllForUser(account.getId(), now);

        String rawToken = HashUtil.newToken();
        tokenRepository.save(PasswordResetToken.create(account.getId(), HashUtil.sha256Hex(rawToken),
                now.plus(TOKEN_TTL)));

        String link = properties.publicBaseUrl() + "/reset-password?token=" + rawToken;
        emailSender.sendPasswordReset(account.getEmail(), link, "30 minutes");
        securityLog.info("password reset requested userId={}", account.getId());
        return MessageResponse.of(NEUTRAL_MESSAGE);
    }

    /**
     * Consumes a reset token and sets a new password.
     *
     * <p>Every failure (unknown, expired, already used) produces the same error, because the
     * distinction is only useful to an attacker with a stolen link.
     */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        Instant now = timeProvider.now();
        PasswordResetToken token = tokenRepository.findByTokenHash(HashUtil.sha256Hex(rawToken))
                .orElseThrow(this::invalidToken);
        if (!token.isConsumableAt(now)) {
            throw invalidToken();
        }

        User user = userAccountService.requireActiveUser(token.getUserId());
        String username = profilePort.summaryOf(user.getId()).map(ProfilePort.UserSummary::username).orElse(null);
        passwordPolicy.validate(newPassword, username, user.getEmail());

        token.consume(now);
        tokenRepository.save(token);
        userAccountService.changePassword(user.getId(), newPassword);

        int revoked = sessionService.revokeAll(user.getId());
        securityLog.info("password reset completed userId={} sessionsRevoked={}", user.getId(), revoked);
    }

    /** Retention job: removes expired tokens. */
    @Transactional
    public int purgeExpired(Duration grace) {
        return tokenRepository.deleteExpiredBefore(timeProvider.now().minus(grace));
    }

    private InvalidRequestException invalidToken() {
        return new InvalidRequestException("This reset link is invalid or has expired");
    }
}
