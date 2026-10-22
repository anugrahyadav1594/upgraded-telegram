package com.realtimechat.messaging.ws;

import com.realtimechat.common.error.ApiException;
import com.realtimechat.common.error.ErrorCode;
import com.realtimechat.common.error.InvalidRequestException;
import com.realtimechat.common.realtime.RealtimeBroadcaster;
import com.realtimechat.common.realtime.RealtimeEvent;
import com.realtimechat.common.realtime.RealtimeEventType;
import com.realtimechat.config.AppProperties;
import com.realtimechat.conversations.service.ConversationAccessPort;
import com.realtimechat.conversations.service.ConversationService;
import com.realtimechat.messaging.dto.ChatDeliveredCommand;
import com.realtimechat.messaging.dto.ChatReadCommand;
import com.realtimechat.messaging.dto.ChatSendCommand;
import com.realtimechat.messaging.dto.ChatTypingCommand;
import com.realtimechat.messaging.dto.MessageDeleteCommand;
import com.realtimechat.messaging.dto.MessageEditCommand;
import com.realtimechat.messaging.dto.MessageReactionCommand;
import com.realtimechat.messaging.event.TypingEvent;
import com.realtimechat.messaging.service.MessageService;
import com.realtimechat.presence.service.TypingService;
import com.realtimechat.users.service.ProfilePort;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * STOMP application endpoints — the realtime write path.
 *
 * <p>Three rules apply to every handler:
 * <ol>
 *   <li><b>The identity is the socket principal.</b> The command records contain no sender or user id
 *       at all, so there is nothing for a client to forge; the principal bound at the handshake is the
 *       only identity in play.</li>
 *   <li><b>Handlers do not answer the topic directly.</b> They call the same services the REST path
 *       uses and let those services publish domain events, which the broadcaster delivers
 *       <em>after commit</em>. Replying from the handler would risk acknowledging a message that a
 *       rollback erases.</li>
 *   <li><b>Failures are reported, never swallowed.</b> Exceptions become a structured
 *       {@code message.failed} frame on {@code /user/queue/errors} so the UI can fail exactly one
 *       optimistic bubble instead of hanging.</li>
 * </ol>
 */
@Controller
@Validated
public class ChatStompController {

    private static final Logger log = LoggerFactory.getLogger(ChatStompController.class);

    private final MessageService messageService;
    private final ConversationService conversationService;
    private final ConversationAccessPort conversationAccessPort;
    private final TypingService typingService;
    private final ProfilePort profilePort;
    private final RealtimeBroadcaster broadcaster;
    private final ApplicationEventPublisher eventPublisher;
    private final AppProperties properties;

    public ChatStompController(MessageService messageService,
                               ConversationService conversationService,
                               ConversationAccessPort conversationAccessPort,
                               TypingService typingService,
                               ProfilePort profilePort,
                               RealtimeBroadcaster broadcaster,
                               ApplicationEventPublisher eventPublisher,
                               AppProperties properties) {
        this.messageService = messageService;
        this.conversationService = conversationService;
        this.conversationAccessPort = conversationAccessPort;
        this.typingService = typingService;
        this.profilePort = profilePort;
        this.broadcaster = broadcaster;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
    }

    /**
     * {@code SEND /app/chat.send}.
     *
     * <p>Returns nothing on purpose: the acknowledgement is the {@code message.ack} event emitted after
     * the transaction commits, which is what makes "the sender has the ack" equivalent to "the message
     * is durable".
     */
    @MessageMapping("/chat.send")
    public void send(@Valid @Payload ChatSendCommand command, Principal principal) {
        StompUserPrincipal user = requirePrincipal(principal);
        MessageService.SendResult result = messageService.send(
                user.userId(), command.conversationId(), command.content(), command.clientMessageId(),
                command.messageType(), command.replyToMessageId());
        log.debug("stomp chat.send user={} conversation={} replayed={}",
                user.userId(), command.conversationId(), result.replayed());
    }

    /** {@code SEND /app/chat.delivered} — the recipient confirms the message reached the device. */
    @MessageMapping("/chat.delivered")
    public void delivered(@Valid @Payload ChatDeliveredCommand command, Principal principal) {
        StompUserPrincipal user = requirePrincipal(principal);
        messageService.markDelivered(user.userId(), command.conversationId(), command.lastMessageId());
    }

    /** {@code SEND /app/chat.read} — the socket twin of {@code POST /conversations/{id}/read}. */
    @MessageMapping("/chat.read")
    public void read(@Valid @Payload ChatReadCommand command, Principal principal) {
        StompUserPrincipal user = requirePrincipal(principal);
        conversationService.markRead(command.conversationId(), user.userId(), command.lastMessageId());
    }

    /**
     * {@code SEND /app/chat.typing} — ephemeral state with a TTL, never persisted.
     *
     * <p>Authorisation comes first: a non-participant must not be able to make a stranger's screen show
     * "typing…". The state itself lives in Redis for a few seconds, and the frame carries the same
     * expiry so a client can clear a stale indicator without waiting for a stop event that may never
     * arrive (a closed laptop lid does not send one).
     */
    @MessageMapping("/chat.typing")
    public void typing(@Valid @Payload ChatTypingCommand command, Principal principal) {
        StompUserPrincipal user = requirePrincipal(principal);
        UUID conversationId = command.conversationId();
        if (!conversationAccessPort.isParticipant(conversationId, user.userId())) {
            throw new InvalidRequestException("Conversation not found");
        }
        typingService.setTyping(conversationId, user.userId(), command.isTyping());

        UUID counterpart = conversationAccessPort.counterpartOf(conversationId, user.userId()).orElse(null);
        if (counterpart == null) {
            return;
        }
        String displayName = profilePort.summaryOf(user.userId())
                .map(ProfilePort.UserSummary::displayName)
                .orElse(user.username());
        long expiresInMs = properties.getMessaging().getTypingTtl().toMillis();
        RealtimeEventType type = command.isTyping()
                ? RealtimeEventType.TYPING_STARTED
                : RealtimeEventType.TYPING_STOPPED;

        // Queue delivery works with no subscription; the topic reaches whoever has the conversation
        // open. Typing state is idempotent, so receiving it twice is harmless.
        eventPublisher.publishEvent(new TypingEvent(conversationId, user.userId(), displayName,
                command.isTyping(), counterpart, expiresInMs));
        Map<String, Object> topicPayload = new LinkedHashMap<>();
        topicPayload.put("conversationId", conversationId);
        topicPayload.put("userId", user.userId());
        topicPayload.put("displayName", displayName);
        topicPayload.put("expiresInMs", expiresInMs);
        broadcaster.publish(RealtimeEvent.toTopic(type, "/topic/conversation/" + conversationId, topicPayload));
    }

    /** {@code SEND /app/message.edit} — the socket twin of {@code PATCH /messages/{id}}. */
    @MessageMapping("/message.edit")
    public void edit(@Valid @Payload MessageEditCommand command, Principal principal) {
        StompUserPrincipal user = requirePrincipal(principal);
        messageService.edit(user.userId(), command.messageId(), command.content());
    }

    /** {@code SEND /app/message.delete}. */
    @MessageMapping("/message.delete")
    public void delete(@Valid @Payload MessageDeleteCommand command, Principal principal) {
        StompUserPrincipal user = requirePrincipal(principal);
        messageService.delete(user.userId(), command.messageId());
    }

    /** {@code SEND /app/message.reaction}. */
    @MessageMapping("/message.reaction")
    public void reaction(@Valid @Payload MessageReactionCommand command, Principal principal) {
        StompUserPrincipal user = requirePrincipal(principal);
        messageService.toggleReaction(user.userId(), command.messageId(), command.emoji());
    }

    /**
     * Turns a failure into a frame the UI can act on.
     *
     * <p>Only the error code comes from the exception; the text is either the service's user-facing
     * message or a generic sentence. Stack traces, SQL state and entity names never reach the socket —
     * the socket is a client surface like any other.
     */
    @MessageExceptionHandler
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public Map<String, Object> handleFailure(Exception failure, Principal principal) {
        UUID userId = principal instanceof StompUserPrincipal stompUser ? stompUser.userId() : null;
        ErrorCode code;
        String message;
        if (failure instanceof ApiException apiException) {
            code = apiException.errorCode();
            message = apiException.getMessage();
        } else if (failure instanceof org.springframework.messaging.handler.annotation.support
                .MethodArgumentNotValidException validationFailure) {
            // Bean Validation on the payload: report the first field message, which is written for a
            // human ("content is required"), not the object path.
            code = ErrorCode.VALIDATION_FAILED;
            message = validationFailure.getBindingResult().getAllErrors().stream()
                    .findFirst()
                    .map(error -> error.getDefaultMessage() == null ? "Invalid request" : error.getDefaultMessage())
                    .orElse("Invalid request");
        } else {
            code = ErrorCode.INTERNAL_ERROR;
            message = "Something went wrong. Please try again.";
        }
        log.warn("stomp command failed code={} userId={} reason={}", code, userId, failure.toString());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code.name());
        payload.put("message", message);
        payload.put("status", code.status().value());
        return payload;
    }

    private StompUserPrincipal requirePrincipal(Principal principal) {
        if (principal instanceof StompUserPrincipal stompUser) {
            return stompUser;
        }
        // Unreachable in practice — the handshake refuses anonymous sockets — but acting without an
        // identity would be worse than failing loudly.
        throw new InvalidRequestException("Session is not authenticated");
    }
}
