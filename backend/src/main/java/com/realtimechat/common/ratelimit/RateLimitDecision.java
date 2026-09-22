package com.realtimechat.common.ratelimit;

import java.time.Duration;

/**
 * Outcome of a rate-limit check.
 *
 * @param allowed          whether the request may proceed
 * @param remaining        remaining permits in the current window (for {@code X-RateLimit-Remaining})
 * @param limit            configured permits per window
 * @param retryAfter       how long until the caller may retry (zero when allowed)
 */
public record RateLimitDecision(boolean allowed, int remaining, int limit, Duration retryAfter) {

    public static RateLimitDecision allow(int remaining, int limit) {
        return new RateLimitDecision(true, Math.max(remaining, 0), limit, Duration.ZERO);
    }

    public static RateLimitDecision deny(int limit, Duration retryAfter) {
        return new RateLimitDecision(false, 0, limit, retryAfter);
    }
}
