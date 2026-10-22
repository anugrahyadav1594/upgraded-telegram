package com.realtimechat.users.repository;

import com.realtimechat.users.entity.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Block relationship storage.
 *
 * <p>Every method that decides whether an interaction is allowed goes through
 * {@code BlockQueryService}, which checks both directions. These queries are the primitives it uses.
 */
public interface UserBlockRepository extends JpaRepository<UserBlock, UserBlock.Key> {

    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    @Query("select b.blockedId from UserBlock b where b.blockerId = :userId")
    List<UUID> findBlockedUserIds(@Param("userId") UUID userId);

    @Query("select b.blockerId from UserBlock b where b.blockedId = :userId")
    List<UUID> findBlockerUserIds(@Param("userId") UUID userId);

    /** Every user id that has a block relationship with the given user, in either direction. */
    @Query("""
            select b.blockedId from UserBlock b where b.blockerId = :userId
            union
            select b.blockerId from UserBlock b where b.blockedId = :userId
            """)
    List<UUID> findAllRelatedUserIds(@Param("userId") UUID userId);

    @Query("select count(b) > 0 from UserBlock b where (b.blockerId = :a and b.blockedId = :b) "
            + "or (b.blockerId = :b and b.blockedId = :a)")
    boolean existsBetween(@Param("a") UUID a, @Param("b") UUID b);

    @Modifying
    @Query("delete from UserBlock b where b.blockerId = :blockerId and b.blockedId = :blockedId")
    int deleteBlock(@Param("blockerId") UUID blockerId, @Param("blockedId") UUID blockedId);

    /** Used when an account is deleted: blocks in both directions disappear with the account. */
    @Modifying
    @Query("delete from UserBlock b where b.blockerId in :ids or b.blockedId in :ids")
    int deleteAllInvolving(@Param("ids") Collection<UUID> ids);
}
