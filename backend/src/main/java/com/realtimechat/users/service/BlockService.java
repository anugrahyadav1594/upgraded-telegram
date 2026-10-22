package com.realtimechat.users.service;

import com.realtimechat.common.error.ConflictException;
import com.realtimechat.common.error.NotFoundException;
import com.realtimechat.users.dto.BlockedUserResponse;
import com.realtimechat.users.entity.UserBlock;
import com.realtimechat.users.repository.UserBlockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Blocking and unblocking.
 *
 * <p>Idempotent by design: blocking an already-blocked user (or unblocking a user who is not
 * blocked) succeeds quietly. A "block" that errors when repeated would leak whether a block already
 * exists and would make the client's retry logic fragile.
 */
@Service
public class BlockService {

    private static final Logger log = LoggerFactory.getLogger(BlockService.class);
    private static final Logger securityLog = LoggerFactory.getLogger("SECURITY");

    private final UserBlockRepository blockRepository;
    private final UserAccountService userAccountService;
    private final BlockQueryService blockQueryService;
    private final ProfilePort profilePort;

    public BlockService(UserBlockRepository blockRepository,
                        UserAccountService userAccountService,
                        BlockQueryService blockQueryService,
                        ProfilePort profilePort) {
        this.blockRepository = blockRepository;
        this.userAccountService = userAccountService;
        this.blockQueryService = blockQueryService;
        this.profilePort = profilePort;
    }

    @Transactional
    public void block(UUID blockerId, UUID blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new ConflictException("You cannot block yourself");
        }
        userAccountService.requireActiveUser(blockedId);
        if (blockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId)) {
            return; // idempotent
        }
        try {
            blockRepository.save(new UserBlock(blockerId, blockedId));
        } catch (DataIntegrityViolationException ex) {
            // Concurrent duplicate: the primary key did its job, the outcome is what was asked for.
            log.debug("concurrent duplicate block ignored blocker={} blocked={}", blockerId, blockedId);
        }
        securityLog.info("user blocked blocker={} blocked={}", blockerId, blockedId);
    }

    @Transactional
    public void unblock(UUID blockerId, UUID blockedId) {
        int removed = blockRepository.deleteBlock(blockerId, blockedId);
        if (removed > 0) {
            securityLog.info("user unblocked blocker={} blocked={}", blockerId, blockedId);
        }
    }

    @Transactional(readOnly = true)
    public List<BlockedUserResponse> listBlocked(UUID blockerId) {
        List<UUID> blockedIds = blockRepository.findBlockedUserIds(blockerId);
        if (blockedIds.isEmpty()) {
            return List.of();
        }
        List<BlockedUserResponse> responses = new java.util.ArrayList<>(blockedIds.size());
        for (UUID blockedId : blockedIds) {
            profilePort.summaryOf(blockedId).ifPresent(summary -> responses.add(new BlockedUserResponse(
                    summary.userId(), summary.username(), summary.displayName(), summary.avatarUrl(), null)));
        }
        return responses;
    }

    @Transactional(readOnly = true)
    public boolean isBlocked(UUID blockerId, UUID blockedId) {
        return blockQueryService.hasBlocked(blockerId, blockedId);
    }

    @Transactional(readOnly = true)
    public void requireNotBlocked(UUID requesterId, UUID targetId) {
        if (blockQueryService.isBlockedEitherWay(requesterId, targetId)) {
            // Deliberately a NOT_FOUND-style refusal handled by callers that need it; here we throw
            // the dedicated BLOCKED error so the sender's client can say why the message failed
            // without revealing who blocked whom.
            throw new com.realtimechat.common.error.BlockedException("You can't message this person");
        }
    }

    /** Guard used by the conversation service when the peer does not exist at all. */
    @Transactional(readOnly = true)
    public void requireExistingUser(UUID userId) {
        if (profilePort.summaryOf(userId).isEmpty()) {
            throw new NotFoundException("User not found");
        }
    }
}
