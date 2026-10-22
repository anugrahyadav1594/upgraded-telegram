package com.realtimechat.notifications.repository;

import com.realtimechat.notifications.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Notification persistence.
 *
 * <p>Ownership is part of every query, not a check applied afterwards: a bulk update or delete filters
 * on {@code user_id}, so a wrong or malicious id cannot touch another user's rows even if a caller
 * forgets to verify ownership first.
 */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query("""
            select n from Notification n
            where n.userId = :userId and (:unreadOnly = false or n.readAt is null)
            order by n.createdAt desc, n.id desc
            """)
    List<Notification> findPage(@Param("userId") UUID userId,
                               @Param("unreadOnly") boolean unreadOnly,
                               Pageable pageable);

    @Query("""
            select n from Notification n
            where n.userId = :userId
              and (:unreadOnly = false or n.readAt is null)
              and (n.createdAt < :cursorCreatedAt
                   or (n.createdAt = :cursorCreatedAt and n.id < :cursorId))
            order by n.createdAt desc, n.id desc
            """)
    List<Notification> findPageAfter(@Param("userId") UUID userId,
                                    @Param("unreadOnly") boolean unreadOnly,
                                    @Param("cursorCreatedAt") Instant cursorCreatedAt,
                                    @Param("cursorId") UUID cursorId,
                                    Pageable pageable);

    @Query("select count(n) from Notification n where n.userId = :userId and n.readAt is null")
    long countUnread(@Param("userId") UUID userId);

    /** Marks specific notifications read; the {@code user_id} filter is the access control. */
    @Modifying
    @Query("""
            update Notification n set n.readAt = :now
            where n.userId = :userId and n.readAt is null and n.id in :ids
            """)
    int markRead(@Param("userId") UUID userId, @Param("ids") Collection<UUID> ids, @Param("now") Instant now);

    @Modifying
    @Query("""
            update Notification n set n.readAt = :now
            where n.userId = :userId and n.readAt is null
            """)
    int markAllRead(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("delete from Notification n where n.userId = :userId and n.id = :id")
    int deleteOwned(@Param("userId") UUID userId, @Param("id") UUID id);

    @Modifying
    @Query("delete from Notification n where n.userId = :userId")
    int deleteAllOwned(@Param("userId") UUID userId);

    /** Retention: prune rows older than the documented window. */
    @Modifying
    @Query("delete from Notification n where n.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
