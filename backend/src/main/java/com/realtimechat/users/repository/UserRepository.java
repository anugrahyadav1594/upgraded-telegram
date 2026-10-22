package com.realtimechat.users.repository;

import com.realtimechat.users.entity.User;
import com.realtimechat.users.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for account identity.
 *
 * <p>Lookups are always by normalised (lowercased) email or by id — the application normalises
 * before calling, and the database enforces the invariant with a CHECK constraint and a unique
 * index on {@code lower(email)}.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String normalizedEmail);

    boolean existsByEmail(String normalizedEmail);

    @Query("select u from User u where u.id in :ids")
    List<User> findAllByIdIn(@Param("ids") Collection<UUID> ids);

    List<User> findAllByStatus(UserStatus status);

    /**
     * Accounts whose soft lock has expired are cleaned up opportunistically by the retention job;
     * this query exists so the job does not need to know the column semantics.
     */
    @Query("select count(u) from User u where u.lockedUntil is not null and u.lockedUntil < :now")
    long countExpiredLocks(@Param("now") java.time.Instant now);
}
