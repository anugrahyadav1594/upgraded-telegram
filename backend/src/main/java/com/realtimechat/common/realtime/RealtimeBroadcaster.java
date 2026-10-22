package com.realtimechat.common.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Turns committed domain events into wire frames and hands them to the bus.
 *
 * <p>This is the single place that knows a frame's exact JSON shape, which keeps the outbound
 * protocol in one file and makes it easy to verify against
 * {@code docs/websocket-protocol.md}.
 *
 * <p>{@link TransactionalEventListener} with {@link TransactionPhase#AFTER_COMMIT} is the crucial
 * detail: a client is only ever told about state that is durable. If the transaction rolls back, no
 * frame is emitted at all.
 */
@Component
public class RealtimeBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(RealtimeBroadcaster.class);

    private final RedisRealtimeBus bus;
    private final ObjectMapper objectMapper;
    private final Timer fanoutTimer;

    public RealtimeBroadcaster(RedisRealtimeBus bus, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this.bus = bus;
        this.objectMapper = objectMapper;
        this.fanoutTimer = Timer.builder("chat.message.fanout.latency")
                .description("Time from domain event handling to bus publish")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    /** Publishes an explicit realtime event (used by WebSocket controllers for targeted replies). */
    public void publish(RealtimeEvent event) {
        Timer.Sample sample = Timer.start();
        String wireFrame = toWireFrame(event);
        if (wireFrame == null) {
            return;
        }
        String channel = event.type() == RealtimeEventType.PRESENCE_UPDATED
                ? RealtimeChannels.PRESENCE_EVENTS
                : RealtimeChannels.CHAT_EVENTS;

        RealtimeEnvelope envelope = new RealtimeEnvelope(
                event.eventId(),
                event.type().name(),
                event.recipients(),
                event.topicDestination(),
                wireFrame,
                event.occurredAt());
        bus.publish(channel, envelope, wireFrame);
        sample.stop(fanoutTimer);
    }

    /** Publishes a domain event that implements {@link RealtimeEventSource}. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDomainEvent(RealtimeEventSource source) {
        try {
            RealtimeEvent event = new RealtimeEvent(
                    UUID.randomUUID(),
                    source.realtimeType(),
                    source.realtimePayload(),
                    source.realtimeRecipients(),
                    source.topicDestination(),
                    Instant.now());
            publish(event);
        } catch (RuntimeException ex) {
            // Failure to notify is never allowed to surface as a failed user action; the data is
            // already committed and clients resync on reconnect.
            log.error("realtime publication failed type={}", source.realtimeType(), ex);
        }
    }

    private String toWireFrame(RealtimeEvent event) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", event.type().wireName());
        frame.put("eventId", event.eventId().toString());
        frame.put("occurredAt", event.occurredAt().toString());
        frame.put("payload", event.payload());
        try {
            return objectMapper.writeValueAsString(frame);
        } catch (JsonProcessingException ex) {
            log.error("could not serialise realtime frame type={}", event.type(), ex);
            return null;
        }
    }
}
