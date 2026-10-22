package com.realtimechat.auth.service;

import com.realtimechat.common.ratelimit.RateLimitDecision;
import com.realtimechat.common.ratelimit.RateLimiter;
import com.realtimechat.common.util.HashUtil;
import com.realtimechat.config.AppProperties;
import com.realtimechat.users.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Brute-force protection for authentication.
 *
 * <p>Two layers, because they stop different attacks:
 * <ul>
 *   <li><b>Per-IP tokens</b> (Redis) stop a spray across many accounts from one source.</li>
 *   <li><b>Per-account soft lock</b> (database) stops a distributed attack that targets one account
 *       from many IPs — an IP limit alone cannot see that pattern.</li>
 * </ul>
 *
 * <p>The account lock is stored on the user row, which is explicit and auditable, and the response
 * for a locked account is the same generic failure as a wrong password. Telling an attacker
 * "this account is locked" would confirm the account exists.
 */
@Component
public class LoginAttemptGuard {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptGuard.class);
    private static final Logger securityLog = LoggerFactory.getLogger("SECURITY");

    private static final String BUCKET_IP = "login-ip";
    private static final String BUCKET_ACCOUNT = "login-account";

    private final RateLimiter rateLimiter;
    private final AppProperties properties;

    public LoginAttemptGuard(RateLimiter rateLimiter, AppProperties properties) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    /** Consumes a per-IP permit. Throws when exhausted. */
    public void checkIp(String clientIp) {
        AppProperties.RateLimit limits = properties.getRateLimit();
        if (!limits.isEnabled()) {
            return;
        }
        RateLimitDecision decision = rateLimiter.consume(BUCKET_IP, clientIp,
                limits.getLoginPerIp(), limits.getLoginWindow());
        if (!decision.allowed()) {
            securityLog.warn("login rate limit tripped ip={}", clientIp);
            throw new com.realtimechat.common.error.RateLimitExceededException(
                    "Too many login attempts. Try again in " + retrySeconds(decision) + " seconds.");
        }
    }

    /** Consumes a per-account permit, keyed by a hash of the identifier (never the raw value). */
    public void checkAccount(String identifier) {
        AppProperties.RateLimit limits = properties.getRateLimit();
        if (!limits.isEnabled()) {
            return;
        }
        RateLimitDecision decision = rateLimiter.consume(BUCKET_ACCOUNT, key(identifier),
                limits.getLoginPerAccount(), limits.getLoginWindow());
        if (!decision.allowed()) {
            securityLog.warn("login attempt limit reached for an account");
            throw new com.realtimechat.common.error.RateLimitExceededException(
                    "Too many login attempts. Try again in " + retrySeconds(decision) + " seconds.");
        }
    }

    /**
     * Records a failed attempt on the user and applies a soft lock after the configured threshold.
     *
     * @return true when this failure triggered the lock
     */
    public boolean recordFailure(User user) {
        AppProperties.RateLimit limits = properties.getRateLimit();
        int threshold = Math.max(1, limits.getLoginPerAccount());
        boolean locked = user.recordFailedLogin(threshold, limits.getLoginWindow(), Instant.now());
        if (locked) {
            securityLog.warn("account soft-locked after repeated failures userId={}", user.getId());
        }
        return locked;
    }

    /** Clears counters after a successful login. */
    public void recordSuccess(User user, String identifier, String clientIp) {
        user.recordSuccessfulLogin(Instant.now());
        try {
            rateLimiter.consume(BUCKET_ACCOUNT, key(identifier), Integer.MAX_VALUE, Duration.ofSeconds(1));
        } catch (RuntimeException ex) {
            log.debug("could not reset account bucket", ex);
        }
        if (clientIp != null) {
            log.debug("login succeeded from {}", clientIp);
        }
    }

    private String key(String identifier) {
        // Hashing keeps raw emails/usernames out of Redis keys and out of any Redis dump or MONITOR
        // output, which is exactly the kind of place personal data tends to leak.
        return HashUtil.sha256Hex(identifier.toLowerCase(java.util.Locale.ROOT)).substring(0, 32);
    }

    private long retrySeconds(RateLimitDecision decision) {
        return Math.max(1, decision.retryAfter().toSeconds());
    }
}
