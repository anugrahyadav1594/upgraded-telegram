package com.realtimechat.users.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Private account identity.
 *
 * <p>This is the <b>only</b> place an email address lives, and it is never mapped to a public API
 * response. Public data is projected from {@code profiles} instead, so leaking an email would
 * require deliberately joining this table into a public DTO.
 *
 * <p>Ids are UUIDs generated in the application (not sequential), so enumeration attacks cannot
 * walk the user base and ids can be exposed where needed without leaking growth rate.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status = UserStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private UserRole role = UserRole.USER;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
        // for JPA
    }

    private User(UUID id, String email, String passwordHash) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.status = UserStatus.ACTIVE;
        this.role = UserRole.USER;
    }

    /** Creates a new account. The caller has already hashed the password with Argon2id. */
    public static User create(String normalizedEmail, String passwordHash) {
        return new User(UUID.randomUUID(), normalizedEmail, passwordHash);
    }

    /** Tombstone account used when a user deletes their account (keeps message references valid). */
    public static UUID tombstoneId() {
        return UUID.fromString("00000000-0000-0000-0000-000000000000");
    }

    public boolean isTombstone() {
        return tombstoneId().equals(id);
    }

    // ------------------------------------------------------------------ domain behaviour

    public void recordSuccessfulLogin(Instant when) {
        this.lastLoginAt = when;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    /**
     * Records a failed login.
     *
     * @param lockThreshold failures allowed before a soft lock
     * @param lockDuration  how long the soft lock lasts
     * @return true when this failure triggered a lock
     */
    public boolean recordFailedLogin(int lockThreshold, java.time.Duration lockDuration, Instant now) {
        this.failedLoginAttempts++;
        if (failedLoginAttempts >= lockThreshold) {
            this.lockedUntil = now.plus(lockDuration);
            return true;
        }
        return false;
    }

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    public void markEmailVerified() {
        this.emailVerified = true;
    }

    public void suspend() {
        this.status = UserStatus.SUSPENDED;
    }

    /**
     * Suspends the account for a bounded period.
     *
     * <p>{@code lockedUntil} is reused as the end of the suspension: it is already there, already
     * indexed, and already the field the login path consults, so a temporary suspension needs no new
     * column and cannot be forgotten by the authentication check. A suspension without an end date is
     * expressed by {@link #suspend()} plus an administrative review, never by a magic far-future date.
     *
     * @param until end of the suspension; {@code null} means indefinite
     */
    public void suspendUntil(Instant until) {
        this.status = UserStatus.SUSPENDED;
        this.lockedUntil = until;
    }

    /** Lifts a suspension and clears any lock, restoring the ability to sign in. */
    public void reinstate() {
        this.status = UserStatus.ACTIVE;
        this.lockedUntil = null;
        this.failedLoginAttempts = 0;
    }

    /** True when a suspension has expired and the account may be treated as active again. */
    public boolean isSuspensionExpired(Instant now) {
        return status == UserStatus.SUSPENDED && lockedUntil != null && !lockedUntil.isAfter(now);
    }

    /**
     * Anonymises the account for deletion while keeping the row (and therefore message and
     * moderation referential integrity) intact. The email is replaced with a non-routable value so
     * the address becomes available for re-registration.
     */
    public void anonymiseForDeletion(Instant now) {
        this.status = UserStatus.DELETED;
        this.email = "deleted-" + id + "@deleted.invalid";
        this.passwordHash = "$argon2id-placeholder$";
        this.lastLoginAt = null;
        this.lockedUntil = null;
        this.failedLoginAttempts = 0;
    }

    // ------------------------------------------------------------------ accessors

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserStatus getStatus() {
        return status;
    }

    public UserRole getRole() {
        return role;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
