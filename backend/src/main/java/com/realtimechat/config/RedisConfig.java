package com.realtimechat.config;

import com.realtimechat.common.realtime.RedisRealtimeBus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis wiring.
 *
 * <p>Redis holds only ephemeral state (presence, typing, rate-limit counters, session cache) and
 * carries Pub/Sub fan-out for the realtime layer. It is never the source of truth for anything a
 * user must be able to read tomorrow — see {@code docs/architecture.md §8}.
 *
 * <p>The listener container is created here rather than relying on auto-configuration so the
 * realtime bus can subscribe deterministically at startup.
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        return template;
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        // Two listener threads: presence bursts must not delay message fan-out.
        container.setTaskExecutor(null);
        container.setSubscriptionExecutor(null);
        return container;
    }

    /** Subscribes the realtime bus to the fan-out channels once the container is ready. */
    @Bean
    public RealtimeBusSubscriptionInitializer realtimeBusSubscriptionInitializer(RedisRealtimeBus bus,
                                                                               RedisMessageListenerContainer container) {
        return new RealtimeBusSubscriptionInitializer(bus, container);
    }

    /** Ensures {@code subscribe()} runs after the listener container has been started. */
    public static class RealtimeBusSubscriptionInitializer
            implements org.springframework.context.ApplicationListener<org.springframework.context.event.ContextRefreshedEvent> {

        private final RedisRealtimeBus bus;
        private final RedisMessageListenerContainer container;
        private boolean initialised;

        public RealtimeBusSubscriptionInitializer(RedisRealtimeBus bus, RedisMessageListenerContainer container) {
            this.bus = bus;
            this.container = container;
        }

        @Override
        public void onApplicationEvent(org.springframework.context.event.ContextRefreshedEvent event) {
            if (!initialised && container.isRunning()) {
                bus.subscribe();
                initialised = true;
            }
        }
    }
}
