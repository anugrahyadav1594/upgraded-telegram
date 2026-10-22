package com.realtimechat.auth.repository;

import com.realtimechat.auth.entity.UserSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Session persistence. All lookups are by token hash or user, never by the raw token. */
public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    Optional<UserSession> findByTokenHash(String tokenHash);

    @Query("""
            select s from UserSession s
            where s.userId = :userId and s.revokedAt is null and s.expiresAt > :now
            order by s.lastUsedAt desc
            """)
    List<UserSession> findActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("update UserSession s set s.revokedAt = :now where s.userId = :userId and s.revokedAt is null")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("""
            update UserSession s set s.revokedAt = :now
            where s.userId = :userId and s.id <> :keepSessionId and s.revokedAt is null
            """)
    int revokeAllExcept(@Param("userId") UUID userId, @Param("keepSessionId") UUID keepSessionId,
                        @Param("now") Instant now);

    @Modifying
    @Query("delete from UserSession s where s.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
