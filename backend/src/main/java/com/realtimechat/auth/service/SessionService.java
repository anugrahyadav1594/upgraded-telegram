package com.realtimechat.auth.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtimechat.auth.entity.UserSession;
import com.realtimechat.auth.repository.UserSessionRepository;
import com.realtimechat.common.util.HashUtil;
import com.realtimechat.common.util.TimeProvider;
import com.realtimechat.common.web.ClientIpResolver;
import com.realtimechat.config.AppProperties;
import com.realtimechat.users.entity.User;
import com.realtimechat.users.entity.UserRole;
import com.realtimechat.users.repository.UserRepository;
import com.realtimechat.users.service.ProfilePort;
import com.realtimechat.users.service.SessionRevocationPort;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Session lifecycle: create, validate, slide, revoke.
 *
 * <p>Three design points matter most:
 *
 * <ol>
 *   <li><b>Hashed storage.</b> The cookie holds a 256-bit random token; the database holds its
 *       SHA-256 hash. A read-only database compromise yields nothing replayable.</li>
 *   <li><b>Two expiry rules.</b> An idle timeout (sliding, refreshed at most once per
 *       {@code app.session.touch-interval}) and an absolute timeout measured from creation. Sliding
 *       alone would let a stolen token live forever; absolute alone would log active users out
 *       mid-conversation.</li>
 *   <li><b>Cache, never source of truth.</b> A validated session is cached in Redis under
 *       {@code session:{tokenHash}} with a short TTL to keep the per-request cost off PostgreSQL.
 *       Every revocation evicts the cache entry, so a revoked session can never be resurrected from
 *       a stale cache value; a cache miss simply falls back to the database.</li>
 * </ol>
 */
@Service
public class SessionService implements SessionRevocationPort {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);
    private static final Logger securityLog = LoggerFactory.getLogger("SECURITY");
    private static final String CACHE_PREFIX = "session:";

    private final UserSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final ProfilePort profilePort;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AppProperties properties;
    private final TimeProvider timeProvider;
    private final ClientIpResolver ipResolver;

    public SessionService(UserSessionRepository sessionRepository,
                          UserRepository userRepository,
                          ProfilePort profilePort,
                          StringRedisTemplate redis,
                          ObjectMapper objectMapper,
                          AppProperties properties,
                          TimeProvider timeProvider,
                          ClientIpResolver ipResolver) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.profilePort = profilePort;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.timeProvider = timeProvider;
        this.ipResolver = ipResolver;
    }

    // ------------------------------------------------------------------ creation

    /**
     * Creates a session and returns the raw token — the only moment it exists outside the cookie.
     *
     * <p>Called on login, on registration and on password reset (all three are new, clean sessions;
     * reusing a token across a privilege change would defeat rotation).
     */
    @Transactional
    public IssuedSession create(User user, HttpServletRequest request) {
        String token = HashUtil.newToken();
        Instant now = timeProvider.now();
        Instant expiresAt = min(now.plus(properties.getSession().getIdleTimeout()),
                now.plus(properties.getSession().getAbsoluteTimeout()));

        InetAddress ip = resolveAddress(request);
        String userAgent = request == null ? null : request.getHeader("User-Agent");

        UserSession session = UserSession.create(user.getId(), HashUtil.sha256Hex(token), ip, userAgent, expiresAt);
        sessionRepository.save(session);
        securityLog.info("session created userId={} sessionId={}", user.getId(), session.getId());
        return new IssuedSession(token, session.getId(), expiresAt);
    }

    /**
     * Converts the socket address into an {@link InetAddress}.
     *
     * <p>A malformed address is not a reason to fail a login: the value is metadata for the session
     * list, so it is dropped and the session is created without it.
     */
    private InetAddress resolveAddress(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String raw = ipResolver.resolve(request);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return InetAddress.getByName(raw);
        } catch (UnknownHostException ex) {
            log.debug("could not parse client address for session metadata");
            return null;
        }
    }

    // ------------------------------------------------------------------ validation

    /**
     * Resolves a raw token into the identity it represents.
     *
     * <p>Returns empty for every invalid case — unknown token, revoked, expired, deleted account —
     * so callers cannot accidentally distinguish them and leak the reason.
     */
    @Transactional
    public Optional<SessionIdentity> resolve(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        String tokenHash = HashUtil.sha256Hex(rawToken);
        Instant now = timeProvider.now();

        Optional<SessionIdentity> cached = readCache(tokenHash, now);
        if (cached.isPresent()) {
            return cached;
        }

        Optional<UserSession> found = sessionRepository.findByTokenHash(tokenHash);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        UserSession session = found.get();
        if (!session.isUsableAt(now)) {
            evict(tokenHash);
            return Optional.empty();
        }

        Optional<User> user = userRepository.findById(session.getUserId());
        if (user.isEmpty() || user.get().getStatus() == com.realtimechat.users.entity.UserStatus.DELETED) {
            evict(tokenHash);
            return Optional.empty();
        }
        String username = profilePort.summaryOf(session.getUserId())
                .map(ProfilePort.UserSummary::username)
                .orElse("");

        if (session.touch(now, properties.getSession().getIdleTimeout(),
                properties.getSession().getTouchInterval(), properties.getSession().getAbsoluteTimeout())) {
            sessionRepository.save(session);
        }

        SessionIdentity identity = new SessionIdentity(
                session.getId(), session.getUserId(), username, user.get().getRole(),
                session.getCreatedAt(), session.getLastUsedAt(), session.getExpiresAt());
        writeCache(tokenHash, identity, now);
        return Optional.of(identity);
    }

    private Optional<SessionIdentity> readCache(String tokenHash, Instant now) {
        try {
            String json = redis.opsForValue().get(CACHE_PREFIX + tokenHash);
            if (json == null) {
                return Optional.empty();
            }
            CachedSession cached = objectMapper.readValue(json, CachedSession.class);
            if (cached.expiresAt == null || !cached.expiresAt.isAfter(now)) {
                evict(tokenHash);
                return Optional.empty();
            }
            return Optional.of(new SessionIdentity(cached.sessionId, cached.userId, cached.username,
                    cached.role, cached.createdAt, cached.lastUsedAt, cached.expiresAt));
        } catch (Exception ex) {
            // A cache problem must never break authentication: fall through to the database.
            log.debug("session cache read failed", ex);
            return Optional.empty();
        }
    }

    private void writeCache(String tokenHash, SessionIdentity identity, Instant now) {
        try {
            Duration ttl = properties.getSession().getCacheTtl();
            Duration untilExpiry = Duration.between(now, identity.expiresAt());
            if (untilExpiry.isNegative() || untilExpiry.isZero()) {
                return;
            }
            Duration effective = untilExpiry.compareTo(ttl) < 0 ? untilExpiry : ttl;
            CachedSession cached = new CachedSession(identity.sessionId(), identity.userId(),
                    identity.username(), identity.role(), identity.createdAt(), identity.lastUsedAt(),
                    identity.expiresAt());
            redis.opsForValue().set(CACHE_PREFIX + tokenHash, objectMapper.writeValueAsString(cached), effective);
        } catch (Exception ex) {
            log.debug("session cache write failed", ex);
        }
    }

    // ------------------------------------------------------------------ management

    @Transactional(readOnly = true)
    public List<UserSession> listActive(UUID userId) {
        return sessionRepository.findActiveByUserId(userId, timeProvider.now());
    }

    /** Revokes one session. Returns false when it is unknown or belongs to another user. */
    @Transactional
    public boolean revoke(UUID userId, UUID sessionId) {
        Optional<UserSession> found = sessionRepository.findById(sessionId);
        if (found.isEmpty() || !found.get().getUserId().equals(userId)) {
            return false;
        }
        UserSession session = found.get();
        if (session.getRevokedAt() == null) {
            session.revoke(timeProvider.now());
            sessionRepository.save(session);
            evict(session.getTokenHash());
            securityLog.info("session revoked userId={} sessionId={}", userId, sessionId);
        }
        return true;
    }

    /** Revokes every session of the user: logout-all, password change, admin suspension, erasure. */
    @Override
    @Transactional
    public int revokeAll(UUID userId) {
        int revoked = sessionRepository.revokeAllForUser(userId, timeProvider.now());
        securityLog.info("all sessions revoked userId={} count={}", userId, revoked);
        return revoked;
    }

    /** Revokes every other session (password change keeps the caller signed in on this device). */
    @Transactional
    public int revokeOthers(UUID userId, UUID keepSessionId) {
        if (keepSessionId == null) {
            return revokeAll(userId);
        }
        int revoked = sessionRepository.revokeAllExcept(userId, keepSessionId, timeProvider.now());
        securityLog.info("other sessions revoked userId={} count={}", userId, revoked);
        return revoked;
    }

    /** Retention job: drops sessions that expired long ago so the table stays small. */
    @Transactional
    public int purgeExpired(Duration grace) {
        return sessionRepository.deleteExpiredBefore(timeProvider.now().minus(grace));
    }

    private void evict(String tokenHash) {
        try {
            redis.delete(CACHE_PREFIX + tokenHash);
        } catch (RuntimeException ex) {
            log.debug("session cache eviction failed", ex);
        }
    }

    private Instant min(Instant a, Instant b) {
        return a.isBefore(b) ? a : b;
    }

    /** Raw token plus identifiers; never logged, never serialised to a client. */
    public record IssuedSession(String rawToken, UUID sessionId, Instant expiresAt) {
    }

    /** Validated session identity (the request-scoped principal source). */
    public record SessionIdentity(UUID sessionId, UUID userId, String username, UserRole role,
                                  Instant createdAt, Instant lastUsedAt, Instant expiresAt) {
    }

    /** Redis cache representation. Ignored properties keep old cache entries readable after upgrades. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class CachedSession {
        public UUID sessionId;
        public UUID userId;
        public String username;
        public UserRole role;
        public Instant createdAt;
        public Instant lastUsedAt;
        public Instant expiresAt;

        CachedSession() {
        }

        CachedSession(UUID sessionId, UUID userId, String username, UserRole role, Instant createdAt,
                      Instant lastUsedAt, Instant expiresAt) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.username = username;
            this.role = role;
            this.createdAt = createdAt;
            this.lastUsedAt = lastUsedAt;
            this.expiresAt = expiresAt;
        }
    }
}
