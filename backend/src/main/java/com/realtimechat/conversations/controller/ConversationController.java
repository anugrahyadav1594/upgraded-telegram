package com.realtimechat.conversations.controller;

import com.realtimechat.common.pagination.PageResponse;
import com.realtimechat.common.web.AuthenticatedUser;
import com.realtimechat.config.AppProperties;
import com.realtimechat.conversations.dto.ConversationResponse;
import com.realtimechat.conversations.dto.CreateConversationRequest;
import com.realtimechat.conversations.dto.CreateConversationResult;
import com.realtimechat.conversations.dto.MarkReadRequest;
import com.realtimechat.conversations.dto.UpdateConversationRequest;
import com.realtimechat.conversations.service.ConversationQueryService;
import com.realtimechat.conversations.service.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * Conversation endpoints.
 *
 * <p>Every handler takes the caller from the session ({@code AuthenticatedUser}) and never accepts a
 * user id in the body — the only client-supplied id here is the <em>peer</em>, which is a request
 * ("talk to this person"), not an assertion of identity.
 */
@RestController
@RequestMapping("/api/v1/conversations")
@Tag(name = "Conversations", description = "Start, list, update and remove conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final ConversationQueryService conversationQueryService;
    private final AppProperties properties;

    public ConversationController(ConversationService conversationService,
                                  ConversationQueryService conversationQueryService,
                                  AppProperties properties) {
        this.conversationService = conversationService;
        this.conversationQueryService = conversationQueryService;
        this.properties = properties;
    }

    @GetMapping
    @Operation(summary = "List the caller's conversations, newest activity first (cursor paginated)")
    public ResponseEntity<PageResponse<ConversationResponse>> list(
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "archived", required = false) Boolean archived,
            AuthenticatedUser currentUser) {
        int pageSize = properties.getPagination().resolveLimit(limit);
        return ResponseEntity.ok(conversationQueryService.list(currentUser.userId(), cursor, pageSize, archived));
    }

    @PostMapping
    @Operation(summary = "Start (or reopen) a DIRECT conversation with a user")
    public ResponseEntity<ConversationResponse> create(@Valid @RequestBody CreateConversationRequest request,
                                                      AuthenticatedUser currentUser) {
        CreateConversationResult result = conversationService.create(currentUser.userId(), request.peerUserId());
        if (!result.created()) {
            return ResponseEntity.ok(result.conversation());
        }
        URI location = UriComponentsBuilder.fromPath("/api/v1/conversations/{id}")
                .buildAndExpand(result.conversation().id())
                .toUri();
        return ResponseEntity.created(location).body(result.conversation());
    }

    @GetMapping("/{conversationId}")
    @Operation(summary = "One conversation (404 when the caller is not a participant)")
    public ResponseEntity<ConversationResponse> get(@PathVariable UUID conversationId,
                                                   AuthenticatedUser currentUser) {
        return ResponseEntity.ok(conversationQueryService.get(conversationId, currentUser.userId()));
    }

    @PatchMapping("/{conversationId}")
    @Operation(summary = "Mute, unmute, archive or unarchive a conversation for the caller only")
    public ResponseEntity<ConversationResponse> update(@PathVariable UUID conversationId,
                                                      @Valid @RequestBody UpdateConversationRequest request,
                                                      AuthenticatedUser currentUser) {
        return ResponseEntity.ok(conversationService.update(conversationId, currentUser.userId(), request));
    }

    @DeleteMapping("/{conversationId}")
    @Operation(summary = "Remove the conversation from the caller's list (the peer keeps theirs)")
    public ResponseEntity<Void> delete(@PathVariable UUID conversationId, AuthenticatedUser currentUser) {
        conversationService.delete(conversationId, currentUser.userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{conversationId}/read")
    @Operation(summary = "Mark every message up to lastMessageId as read")
    public ResponseEntity<Void> markRead(@PathVariable UUID conversationId,
                                        @Valid @RequestBody MarkReadRequest request,
                                        AuthenticatedUser currentUser) {
        conversationService.markRead(conversationId, currentUser.userId(), request.lastMessageId());
        return ResponseEntity.noContent().build();
    }
}
