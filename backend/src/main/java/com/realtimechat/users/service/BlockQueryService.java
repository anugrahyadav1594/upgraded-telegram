package com.realtimechat.users.service;

import com.realtimechat.users.repository.UserBlockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only block checks, exposed to other modules (messaging, conversations, presence,
 * notifications, profiles).
 *
 * <p>This is the single place that answers "may these two users interact?" so a new feature cannot
 * forget to check. It always considers both directions:
 *
 * <ul>
 *   <li>A blocked B → B cannot message A, start a conversation with A, see A's profile, or observe
 *       A's presence.</li>
 *   <li>A blocked B → A also cannot message B. This is intentional: allowing the blocker to
 *       continue interacting would let them harass the person they blocked, and it would let a
 *       blocked user prove they were blocked by observing that messages suddenly fail only in one
 *       direction.</li>
 * </ul>
 */
@Service
public class BlockQueryService {

    private final UserBlockRepository blockRepository;

    public BlockQueryService(UserBlockRepository blockRepository) {
        this.blockRepository = blockRepository;
    }

    @Transactional(readOnly = true)
    public boolean isBlockedEitherWay(UUID userA, UUID userB) {
        if (userA == null || userB == null || userA.equals(userB)) {
            return false;
        }
        return blockRepository.existsBetween(userA, userB);
    }

    @Transactional(readOnly = true)
    public boolean hasBlocked(UUID blocker, UUID blocked) {
        return blockRepository.existsByBlockerIdAndBlockedId(blocker, blocked);
    }

    /** All users that cannot be shown to, or interacted with by, the given user. */
    @Transactional(readOnly = true)
    public Set<UUID> usersInvisibleTo(UUID userId) {
        Set<UUID> related = new HashSet<>(blockRepository.findAllRelatedUserIds(userId));
        return related;
    }

    @Transactional(readOnly = true)
    public Set<UUID> blockedBy(UUID userId) {
        return new HashSet<>(blockRepository.findBlockedUserIds(userId));
    }

    @Transactional(readOnly = true)
    public List<UUID> blockedUserIds(UUID userId) {
        return blockRepository.findBlockedUserIds(userId);
    }

    @Transactional(readOnly = true)
    public List<UUID> blockerUserIds(UUID userId) {
        return blockRepository.findBlockerUserIds(userId);
    }
}
