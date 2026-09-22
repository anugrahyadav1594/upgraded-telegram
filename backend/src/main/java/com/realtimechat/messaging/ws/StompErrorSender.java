package com.realtimechat.messaging.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtimechat.common.realtime.RealtimeEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pushes a structured error to one socket.
 *
 * <p>Realtime failures must be visible to the client: silently dropping a rejected frame would leave
 * the UI spinning on an optimistic bubble forever. The frame uses the same envelope shape as every
 * other event ({@code type, eventId, occurredAt, payload}) so the client has exactly one parser, and
 * the payload carries a stable {@code code} the UI can translate into a message.
 */
@Component
public class StompErrorSender {

    private static final Logger log = LoggerFactory.getLogger(StompErrorSender.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public StompErrorSender(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * @param recipientUserId the user whose sockets should receive the error
     * @param code            stable machine-readable code, e.g. {@code RATE_LIMITED}
     * @param message         human-readable explanation (never contains internal detail)
     * @param context         extra identifiers ({@code conversationId}, {@code clientMessageId}) so
     *                        the UI can attach the error to the exact optimistic bubble
     */
    public void send(UUID recipientUserId, String code, String message, Map<String, Object> context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code);
        payload.put("message", message);
        if (context != null) {
            payload.putAll(context);
        }
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", "error");
        frame.put("eventId", UUID.randomUUID().toString());
        frame.put("occurredAt", Instant.now().toString());
        frame.put("payload", payload);
        try {
            messagingTemplate.convertAndSendToUser(recipientUserId.toString(),
                    RealtimeEventType.Target.ERRORS.destination(), objectMapper.writeValueAsString(frame));
        } catch (Exception ex) {
            log.warn("could not deliver realtime error userId={} code={}", recipientUserId, code, ex);
        }
    }

    public void send(UUID recipientUserId, String code, String message) {
        send(recipientUserId, code, message, Map.of());
    }
}
