package com.realtimechat.users.controller;

import com.realtimechat.common.web.AuthenticatedUser;
import com.realtimechat.users.dto.BlockedUserResponse;
import com.realtimechat.users.service.BlockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Blocking endpoints.
 *
 * <p>Both operations are idempotent and neither reveals whether a block already existed. The effect
 * of a block is enforced server-side on every interaction path — messaging, conversation creation,
 * profile reads, presence and typing — so hiding UI elements is only a convenience, never the
 * control.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Blocks", description = "Block and unblock users")
public class BlockController {

    private final BlockService blockService;

    public BlockController(BlockService blockService) {
        this.blockService = blockService;
    }

    @PostMapping("/users/{userId}/block")
    @Operation(summary = "Block a user (idempotent)")
    public ResponseEntity<Void> block(@PathVariable UUID userId, AuthenticatedUser currentUser) {
        blockService.block(currentUser.userId(), userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/users/{userId}/block")
    @Operation(summary = "Unblock a user (idempotent)")
    public ResponseEntity<Void> unblock(@PathVariable UUID userId, AuthenticatedUser currentUser) {
        blockService.unblock(currentUser.userId(), userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/blocks")
    @Operation(summary = "List blocked users")
    public ResponseEntity<List<BlockedUserResponse>> list(AuthenticatedUser currentUser) {
        return ResponseEntity.ok(blockService.listBlocked(currentUser.userId()));
    }
}
