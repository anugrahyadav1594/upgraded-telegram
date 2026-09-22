package com.realtimechat.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;

/**
 * A server-managed session.
 *
 * <p>The client holds an opaque 256-bit token in an {@code HttpOnly} cookie; the database stores
 * only its SHA-256 hash. A database dump therefore cannot be replayed as a login, and the token
 * never appears in JavaScript, logs or error messages.
 *
 * <p>Two independent expiry rules apply: an <b>idle</b> timeout (sliding, extended at most once per
 * configured interval) and an <b>absolute</b> timeout from creation. Sliding alone would let a
 * stolen token live forever; absolute alone would log active users out mid-conversation.
 */
@Entity
@Table(name = "user_sessions")
public class UserSession {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, updatable = false)
    private String tokenHash;

    /**
     * PostgreSQL {@code inet} column. Mapped as {@link InetAddress} (Hibernate's native mapping for
     * that type) so the database validates the value and the API can never store a spoofed
     * {@code X-Forwarded-For} string — only the socket address of the connecting client is kept.
     */
    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address")
    private InetAddress ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected UserSession() {
        // for JPA
    }

    public static UserSession create(UUID userId, String tokenHash, InetAddress ipAddress, String userAgent,
                                     Instant expiresAt) {
        UserSession session = new UserSession();
        session.id = UUID.randomUUID();
        session.userId = userId;
        session.tokenHash = tokenHash;
        session.ipAddress = ipAddress;
        session.userAgent = truncate(userAgent, 256);
        session.expiresAt = expiresAt;
        return session;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** True when the session can still authenticate at the given instant. */
    public boolean isUsableAt(Instant now) {
        return revokedAt == null && expiresAt != null && expiresAt.isAfter(now);
    }

    /**
     * Extends the idle window, capped by the absolute expiry set at creation.
     *
     * @return true when the session was actually touched (the caller may skip the write otherwise)
     */
    public boolean touch(Instant now, java.time.Duration idleTimeout, java.time.Duration touchInterval,
                         java.time.Duration absoluteTimeout) {
        if (lastUsedAt != null && lastUsedAt.plus(touchInterval).isAfter(now)) {
            return false; // avoid a write on every single request
        }
        this.lastUsedAt = now;
        Instant proposed = now.plus(idleTimeout);
        // Never extend beyond the absolute expiry, so an active session still ends eventually.
        Instant absoluteLimit = createdAt == null ? proposed : createdAt.plus(absoluteTimeout);
        this.expiresAt = proposed.isBefore(absoluteLimit) ? proposed : absoluteLimit;
        return true;
    }

    public void revoke(Instant when) {
        this.revokedAt = when;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public InetAddress getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
