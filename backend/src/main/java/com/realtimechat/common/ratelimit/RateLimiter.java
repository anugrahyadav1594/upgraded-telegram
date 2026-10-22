package com.realtimechat.common.ratelimit;

import java.time.Duration;

/**
 * Fixed-window rate limiter backed by Redis (see {@code docs/architecture.md §8}).
 *
 * <p>Abstraction exists because rate limiting must be:
 * <ul>
 *   <li>shared across application instances (a per-JVM counter is trivially bypassed by
 *       horizontal scaling), and</li>
 *   <li>replaceable in tests (a no-op or in-memory implementation) without touching callers.</li>
 * </ul>
 */
public interface RateLimiter {

    /**
     * Consumes one permit from a bucket.
     *
     * @param bucket   logical operation, e.g. {@code login}, {@code register}, {@code messages}
     * @param key      the limiting subject (IP address, user id, email hash)
     * @param limit    permits allowed per window
     * @param window   window length
     * @return the decision, including remaining permits and retry hint
     */
    RateLimitDecision consume(String bucket, String key, int limit, Duration window);

    /** Reads the current state without consuming a permit (used for response headers). */
    RateLimitDecision peek(String bucket, String key, int limit, Duration window);
}
