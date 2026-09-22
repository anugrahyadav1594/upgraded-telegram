package com.realtimechat.common.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis implementation of {@link RateLimiter} using a fixed window counter.
 *
 * <p>Why fixed windows: a single {@code INCR} + {@code EXPIRE} is atomic enough, cheap and trivially
 * understandable, and the abuse patterns being defended against (credential stuffing, message
 * floods, report spam) do not need the precision of a sliding log. The trade-off — up to 2× the
 * limit across a window boundary — is documented and accepted.
 *
 * <p>Failure policy: if Redis is unreachable the limiter <b>fails closed</b> for authentication
 * operations (it denies) rather than silently allowing unlimited login attempts. A metric/log line
 * is emitted so the degradation is visible.
 */
@Component
public class RedisRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);
    private static final String KEY_PREFIX = "rl:";

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public RateLimitDecision consume(String bucket, String key, int limit, Duration window) {
        if (limit <= 0) {
            return RateLimitDecision.allow(Integer.MAX_VALUE, Integer.MAX_VALUE);
        }
        String redisKey = redisKey(bucket, key);
        try {
            Long count = redisTemplate.opsForValue().increment(redisKey);
            if (count == null) {
                return RateLimitDecision.allow(limit, limit);
            }
            if (count == 1L) {
                redisTemplate.expire(redisKey, window);
            }
            if (count > limit) {
                return RateLimitDecision.deny(limit, remainingTtl(redisKey, window));
            }
            return RateLimitDecision.allow((int) (limit - count), limit);
        } catch (RuntimeException ex) {
            // Fail closed: refusing an action is safer than granting unlimited retries of a
            // protected operation when the limiter itself is unreachable.
            log.error("rate limiter unavailable bucket={} — failing closed", bucket, ex);
            return RateLimitDecision.deny(limit, window);
        }
    }

    @Override
    public RateLimitDecision peek(String bucket, String key, int limit, Duration window) {
        if (limit <= 0) {
            return RateLimitDecision.allow(Integer.MAX_VALUE, Integer.MAX_VALUE);
        }
        String redisKey = redisKey(bucket, key);
        try {
            String value = redisTemplate.opsForValue().get(redisKey);
            int used = value == null ? 0 : Integer.parseInt(value);
            if (used >= limit) {
                return RateLimitDecision.deny(limit, remainingTtl(redisKey, window));
            }
            return RateLimitDecision.allow(limit - used, limit);
        } catch (RuntimeException ex) {
            log.warn("rate limiter peek failed bucket={}", bucket, ex);
            return RateLimitDecision.allow(limit, limit);
        }
    }

    private Duration remainingTtl(String redisKey, Duration fallback) {
        Long ttl = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
        if (ttl == null || ttl <= 0) {
            return fallback;
        }
        return Duration.ofSeconds(ttl);
    }

    private String redisKey(String bucket, String key) {
        return KEY_PREFIX + bucket + ":" + key;
    }

    /** Resets counters — used by tests and by an operator after resolving an incident. */
    public void reset(String bucket, String key) {
        redisTemplate.delete(List.of(redisKey(bucket, key)));
    }

    /** Current instant, exposed for deterministic tests. */
    protected Instant now() {
        return Instant.now();
    }
}
