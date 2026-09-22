package com.realtimechat.common.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Cross-instance fan-out over Redis Pub/Sub.
 *
 * <p>Redis Pub/Sub is deliberately used as an <b>at-most-once hint transport</b>: it never holds
 * message history (PostgreSQL does) and a dropped frame is recovered by the client's
 * resynchronisation after reconnect. That trade-off buys simplicity and single-digit-millisecond
 * fan-out without running Kafka for a feature whose durability is already guaranteed elsewhere.
 *
 * <p>Graceful degradation: if Redis is unreachable, the frame is delivered to local sessions only
 * and a warning is logged. Users on other instances recover through REST resync — never a silent
 * data loss, because nothing was ever stored here.
 */
@Component
public class RedisRealtimeBus implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisRealtimeBus.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisMessageListenerContainer listenerContainer;
    private final LocalSessionRegistry registry;
    private final ObjectMapper objectMapper;

    public RedisRealtimeBus(StringRedisTemplate redisTemplate,
                            RedisMessageListenerContainer listenerContainer,
                            LocalSessionRegistry registry,
                            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.listenerContainer = listenerContainer;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    /** Subscribes this instance to the fan-out channels. Called once by the Redis configuration. */
    public void subscribe() {
        listenerContainer.addMessageListener(this, new ChannelTopic(RealtimeChannels.CHAT_EVENTS));
        listenerContainer.addMessageListener(this, new ChannelTopic(RealtimeChannels.PRESENCE_EVENTS));
        log.info("realtime bus subscribed to {} and {}",
                RealtimeChannels.CHAT_EVENTS, RealtimeChannels.PRESENCE_EVENTS);
    }

    /** Publishes an envelope; falls back to local-only delivery when Redis is unavailable. */
    public void publish(String channel, RealtimeEnvelope envelope, String wireFrame) {
        try {
            String json = objectMapper.writeValueAsString(envelope);
            redisTemplate.convertAndSend(channel, json);
        } catch (Exception ex) {
            log.warn("redis publish failed channel={} type={} — falling back to local delivery", channel,
                    envelope.type(), ex);
            deliverLocally(envelope.recipients(), envelope.type(), envelope.topicDestination(), wireFrame);
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String json = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            RealtimeEnvelope envelope = objectMapper.readValue(json, RealtimeEnvelope.class);
            RealtimeEventType type = RealtimeEventType.valueOf(envelope.type());
            deliverLocally(envelope.recipients(), type, envelope.topicDestination(), envelope.payloadJson());
        } catch (Exception ex) {
            // A malformed envelope must never kill the listener; log and drop.
            log.error("discarding malformed realtime envelope", ex);
        }
    }

    private void deliverLocally(Collection<UUID> recipients, RealtimeEventType type,
                               String topicDestination, String wireFrame) {
        registry.deliver(recipients == null ? Set.of() : recipients, type, topicDestination, wireFrame);
    }
}
