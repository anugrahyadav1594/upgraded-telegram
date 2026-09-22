package com.realtimechat.config;

import com.realtimechat.messaging.ws.StompUserPrincipal;
import com.realtimechat.messaging.ws.WebSocketAuthChannelInterceptor;
import com.realtimechat.messaging.ws.WebSocketAuthHandshakeInterceptor;
import com.realtimechat.messaging.ws.WebSocketSubscriptionInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * STOMP over native WebSocket at {@code /ws}.
 *
 * <p>Decisions and their rationale (see {@code docs/websocket-protocol.md} and
 * {@code docs/architecture.md §7}):
 * <ul>
 *   <li><b>No SockJS fallback.</b> Every supported browser has WebSocket; SockJS would add an XHR
 *       transport with its own security review for no functional gain.</li>
 *   <li><b>Authentication at the handshake.</b> The session cookie travels with the upgrade
 *       request, so an unauthenticated client is rejected with 401 before a single STOMP frame is
 *       processed.</li>
 *   <li><b>Explicit Origin allow-list.</b> Browsers send {@code Origin} on WebSocket handshakes;
 *       requiring it to match the configured frontend origin blocks cross-site socket hijacking
 *       (a SameSite=Lax cookie is the second layer).</li>
 *   <li><b>Bounded transport.</b> Frame size, send buffer and send time limits are enforced so one
 *       slow or abusive client cannot exhaust server memory.</li>
 *   <li><b>Simple broker.</b> In-process {@code SimpleBroker} — the documented single-instance
 *       component. Delivery correctness across instances comes from the Redis fan-out in
 *       {@code RedisRealtimeBus}, not from broker subscriptions; replacing this with a broker relay
 *       is the documented scale-out step.</li>
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final AppProperties properties;
    private final WebSocketAuthHandshakeInterceptor handshakeInterceptor;
    private final WebSocketAuthChannelInterceptor authChannelInterceptor;
    private final WebSocketSubscriptionInterceptor subscriptionInterceptor;

    public WebSocketConfig(AppProperties properties,
                           WebSocketAuthHandshakeInterceptor handshakeInterceptor,
                           WebSocketAuthChannelInterceptor authChannelInterceptor,
                           WebSocketSubscriptionInterceptor subscriptionInterceptor) {
        this.properties = properties;
        this.handshakeInterceptor = handshakeInterceptor;
        this.authChannelInterceptor = authChannelInterceptor;
        this.subscriptionInterceptor = subscriptionInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // /topic   — conversation-scoped ephemeral events (typing), subscription-authorized
        // /queue   — per-user private queues (/user/queue/messages, /user/queue/events)
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{
                        properties.getWebsocket().getHeartbeatIncoming().toMillis(),
                        properties.getWebsocket().getHeartbeatOutgoing().toMillis()})
                .setTaskScheduler(webSocketHeartbeatScheduler());
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        registry.setPreservePublishOrder(true);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(properties.getWebsocket().getEndpoint())
                .setAllowedOriginPatterns(properties.getWebsocket().getAllowedOrigins().toArray(String[]::new))
                .addInterceptors(handshakeInterceptor);
        // No .withSockJS(): see the class comment.
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor, subscriptionInterceptor);
        registration.taskExecutor()
                .corePoolSize(4)
                .maxPoolSize(16)
                .queueCapacity(500);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor()
                .corePoolSize(4)
                .maxPoolSize(16)
                .queueCapacity(1000);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration
                .setMessageSizeLimit(properties.getWebsocket().getMaxFrameSizeBytes())
                .setSendBufferSizeLimit(properties.getWebsocket().getSendBufferSizeLimit())
                .setSendTimeLimit(properties.getWebsocket().getSendTimeLimitMs());
    }

    /** Heartbeat scheduler for the simple broker (its own small pool, separate from request threads). */
    @Bean
    public ThreadPoolTaskScheduler webSocketHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.setDaemon(true);
        scheduler.initialize();
        return scheduler;
    }

    /** Exposed for the handshake handler to name the client-side principal. */
    public static Class<StompUserPrincipal> principalType() {
        return StompUserPrincipal.class;
    }
}
