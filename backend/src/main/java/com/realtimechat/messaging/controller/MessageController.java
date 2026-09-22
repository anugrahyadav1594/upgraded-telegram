package com.realtimechat.messaging.controller;

import com.realtimechat.common.pagination.PageResponse;
import com.realtimechat.common.web.AuthenticatedUser;
import com.realtimechat.config.AppProperties;
import com.realtimechat.conversations.service.ConversationAccessPort;
import com.realtimechat.messaging.dto.ChatMessageResponse;
import com.realtimechat.messaging.dto.EditMessageRequest;
import com.realtimechat.messaging.dto.ReactionRequest;
import com.realtimechat.messaging.dto.SendMessageRequest;
import com.realtimechat.messaging.service.MessageQueryService;
import com.realtimechat.messaging.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Message endpoints.
 *
 * <p>The REST and WebSocket write paths deliberately share the same services, so they cannot drift in
 * behaviour: a message sent over HTTP and one sent over STOMP go through identical authorisation,
 * idempotency and rate limiting, and produce identical events. REST exists for portability (no socket,
 * restrictive proxies) and for tests; the socket is the fast path.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Messages", description = "History, sending, editing, deleting, reactions and receipts")
public class MessageController {

    private final MessageService messageService;
    private final MessageQueryService messageQueryService;
    private final ConversationAccessPort conversationAccessPort;
    private final AppProperties properties;

    public MessageController(MessageService messageService,
                             MessageQueryService messageQueryService,
                             ConversationAccessPort conversationAccessPort,
                             AppProperties properties) {
        this.messageService = messageService;
        this.messageQueryService = messageQueryService;
        this.conversationAccessPort = conversationAccessPort;
        this.properties = properties;
    }

    @GetMapping("/conversations/{conversationId}/messages")
    @Operation(summary = "History page, newest first; direction=newer walks forward from a cursor")
    public ResponseEntity<PageResponse<ChatMessageResponse>> history(
            @PathVariable UUID conversationId,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "direction", required = false, defaultValue = "older") String direction,
            AuthenticatedUser currentUser) {
        int pageSize = properties.getPagination().resolveMessageLimit(limit);
        return ResponseEntity.ok(messageQueryService.history(
                currentUser.userId(), conversationId, cursor, pageSize, direction));
    }

    /**
     * Missed-message resynchronisation.
     *
     * <p>Keyed by {@code sequence} rather than by time: the watermark is assigned by the database, so a
     * client with a wrong or drifting clock still receives exactly what it missed. Clients drain this
     * endpoint page by page after a reconnect until {@code hasMore} is false.
     */
    @GetMapping("/conversations/{conversationId}/messages/resync")
    @Operation(summary = "Everything after a sequence watermark (reconnect resync)")
    public ResponseEntity<PageResponse<ChatMessageResponse>> resync(
            @PathVariable UUID conversationId,
            @RequestParam(name = "afterSequence", required = false, defaultValue = "0") Long afterSequence,
            @RequestParam(name = "limit", required = false) Integer limit,
            AuthenticatedUser currentUser) {
        int pageSize = properties.getPagination().resolveMessageLimit(limit);
        List<ChatMessageResponse> items = messageQueryService.resync(
                currentUser.userId(), conversationId, afterSequence, pageSize);
        boolean hasMore = items.size() == pageSize;
        return ResponseEntity.ok(new PageResponse<>(items, null, hasMore, pageSize));
    }

    @PostMapping("/conversations/{conversationId}/messages")
    @Operation(summary = "Send a message (idempotent on clientMessageId)")
    public ResponseEntity<ChatMessageResponse> send(@PathVariable UUID conversationId,
                                                   @Valid @RequestBody SendMessageRequest request,
                                                   AuthenticatedUser currentUser) {
        MessageService.SendResult result = messageService.send(currentUser.userId(), conversationId,
                request.content(), request.clientMessageId(), request.messageType(), request.replyToMessageId());
        if (result.replayed()) {
            // The stored message is returned with 200 and a marker header: a retried POST is not an
            // error, but it must be distinguishable from a fresh send by an integrator.
            return ResponseEntity.ok()
                    .header("Idempotent-Replay", "true")
                    .body(result.message());
        }
        URI location = URI.create("/api/v1/messages/" + result.message().id());
        return ResponseEntity.created(location).body(result.message());
    }

    @PatchMapping("/messages/{messageId}")
    @Operation(summary = "Edit your own message within the edit window")
    public ResponseEntity<ChatMessageResponse> edit(@PathVariable UUID messageId,
                                                   @Valid @RequestBody EditMessageRequest request,
                                                   AuthenticatedUser currentUser) {
        return ResponseEntity.ok(messageService.edit(currentUser.userId(), messageId, request.content()));
    }

    @DeleteMapping("/messages/{messageId}")
    @Operation(summary = "Delete your own message (soft delete, tombstoned for everyone)")
    public ResponseEntity<Void> delete(@PathVariable UUID messageId, AuthenticatedUser currentUser) {
        messageService.delete(currentUser.userId(), messageId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/messages/{messageId}/read")
    @Operation(summary = "Mark one message as read (recipient only)")
    public ResponseEntity<Void> markRead(@PathVariable UUID messageId, AuthenticatedUser currentUser) {
        messageService.markRead(currentUser.userId(), messageId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/messages/{messageId}/reaction")
    @Operation(summary = "Toggle a reaction and return the message with aggregated reactions")
    public ResponseEntity<ChatMessageResponse> react(@PathVariable UUID messageId,
                                                    @Valid @RequestBody ReactionRequest request,
                                                    AuthenticatedUser currentUser) {
        return ResponseEntity.ok(messageService.toggleReaction(currentUser.userId(), messageId, request.emoji()));
    }

    @DeleteMapping("/messages/{messageId}/reaction")
    @Operation(summary = "Remove a reaction (idempotent) and return the updated message")
    public ResponseEntity<ChatMessageResponse> removeReaction(@PathVariable UUID messageId,
                                                             @RequestParam(name = "emoji") String emoji,
                                                             AuthenticatedUser currentUser) {
        // The service toggles; delete-of-absent is a no-op, so the endpoint stays idempotent.
        ChatMessageResponse response = messageService.toggleReaction(currentUser.userId(), messageId, emoji);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(response);
    }
}
