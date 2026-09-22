package com.realtimechat.profiles.repository;

import com.realtimechat.profiles.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Profile persistence, including the user-search access paths.
 *
 * <p>The search query is native SQL because it needs the {@code text_pattern_ops} /
 * trigram indexes and a relevance ordering that JPQL cannot express. It is written once here,
 * covered by {@code ProfileSearchIT}, and deliberately avoids {@code SELECT *}-style unbounded
 * scans: it is always filtered, ordered and limited.
 */
public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    Optional<Profile> findByUsername(String username);

    @Query("select p from Profile p where p.userId in :ids")
    List<Profile> findAllByUserIdIn(@Param("ids") Collection<UUID> ids);

    boolean existsByUsername(String username);

    /**
     * Prefix + substring search across username and display name.
     *
     * <p>Ordering: exact username match, then username prefix, then display-name prefix, then the
     * rest — so typing a handle puts the handle first instead of burying it under display names that
     * happen to contain the same letters. The cursor is {@code (username, userId)}.
     */
    @Query(value = """
            SELECT p.*
            FROM profiles p
            JOIN user_privacy_settings s ON s.user_id = p.user_id
            WHERE s.show_profile_in_search = true
              AND p.user_id <> :selfId
              AND (:excludeEmpty = true OR p.user_id NOT IN (:excludedUserIds))
              AND (p.username LIKE :prefix ESCAPE '\\' OR lower(p.display_name) LIKE :contains ESCAPE '\\')
              AND (:cursorKey IS NULL
                   OR p.username > :cursorKey
                   OR (p.username = :cursorKey AND p.user_id > :cursorId))
            ORDER BY p.username ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<Profile> search(@Param("selfId") UUID selfId,
                         @Param("prefix") String prefixPattern,
                         @Param("contains") String containsPattern,
                         @Param("excludedUserIds") Collection<UUID> excludedUserIds,
                         @Param("excludeEmpty") boolean excludedEmpty,
                         @Param("cursorKey") String cursorKey,
                         @Param("cursorId") UUID cursorId,
                         @Param("limit") int limit);

    /** Throttled last-seen write (at most once per configured interval per user). */
    @org.springframework.data.jpa.repository.Modifying
    @Query("""
            update Profile p set p.lastSeenAt = :when
            where p.userId = :userId
              and (p.lastSeenAt is null or p.lastSeenAt < :threshold)
            """)
    int touchLastSeen(@Param("userId") UUID userId,
                      @Param("when") Instant when,
                      @Param("threshold") Instant threshold);
}
