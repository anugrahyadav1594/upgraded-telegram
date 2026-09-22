package com.realtimechat.presence.service;

import com.realtimechat.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Typing indicators: pure ephemeral state with a TTL, <b>never</b> written to PostgreSQL.
 *
 * <p>Why TTL rather than explicit start/stop bookkeeping: a client that closes its laptop lid never
 * sends "stopped typing". A 5-second key means the indicator disappears on its own, so the failure
 * mode is "the dots go away slightly early", not "the dots are stuck forever".
 */
@Service
public class TypingService {

    private static final Logger log = LoggerFactory.getLogger(TypingService.class);
    private static final String KEY_PREFIX = "typing:";

    private final StringRedisTemplate redis;
    private final AppProperties properties;

    public TypingService(StringRedisTemplate redis, AppProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    /** Marks the user as typing in the conversation; the key expires by itself. */
    public void setTyping(UUID conversationId, UUID userId, boolean typing) {
        String key = key(conversationId, userId);
        try {
            if (typing) {
                redis.opsForValue().set(key, "1", properties.getMessaging().getTypingTtl());
            } else {
                redis.delete(key);
            }
        } catch (RuntimeException ex) {
            // Losing a typing indicator is invisible to the conversation's integrity.
            log.debug("typing state update failed conversationId={}", conversationId, ex);
        }
    }

    public boolean isTyping(UUID conversationId, UUID userId) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(key(conversationId, userId)));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private String key(UUID conversationId, UUID userId) {
        return KEY_PREFIX + conversationId + ":" + userId;
    }
}
