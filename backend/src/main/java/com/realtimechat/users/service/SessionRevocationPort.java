package com.realtimechat.users.service;

import java.util.UUID;

/**
 * Port for revoking a user's sessions, implemented by the {@code auth} module.
 *
 * <p>Account deletion must end every session immediately: leaving a live cookie behind after the
 * account is gone would let a stale client keep acting as a deleted user (and would keep failing
 * loudly in the logs). Declaring the port here keeps the dependency direction one-way
 * (auth → users) while still allowing the erasure flow to be complete.
 */
public interface SessionRevocationPort {

    /** Revokes every active session of the user; returns how many were revoked. */
    int revokeAll(UUID userId);
}
