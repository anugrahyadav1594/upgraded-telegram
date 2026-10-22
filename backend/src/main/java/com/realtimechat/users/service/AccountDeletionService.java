package com.realtimechat.users.service;

import com.realtimechat.common.realtime.RealtimeBroadcaster;
import com.realtimechat.common.realtime.RealtimeEvent;
import com.realtimechat.common.realtime.RealtimeEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Account erasure.
 *
 * <p>Order matters and is deliberate:
 * <ol>
 *   <li>sessions are revoked <b>first</b>, so no request can be authenticated while the account is
 *       being torn down;</li>
 *   <li>personal data is erased and authored content is anonymised onto the tombstone identity;</li>
 *   <li>connected devices are told to drop their state ({@code session.revoked}) — a client that
 *       keeps rendering a deleted account's UI would be a confusing, and in shared-device terms
 *       dangerous, failure mode.</li>
 * </ol>
 */
@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);
    private static final Logger securityLog = LoggerFactory.getLogger("SECURITY");

    private final UserAccountService userAccountService;
    private final SessionRevocationPort sessionRevocationPort;
    private final RealtimeBroadcaster broadcaster;

    public AccountDeletionService(UserAccountService userAccountService,
                                  SessionRevocationPort sessionRevocationPort,
                                  RealtimeBroadcaster broadcaster) {
        this.userAccountService = userAccountService;
        this.sessionRevocationPort = sessionRevocationPort;
        this.broadcaster = broadcaster;
    }

    @Transactional
    public void deleteAccount(UUID userId) {
        int revoked = sessionRevocationPort.revokeAll(userId);
        userAccountService.deleteAccount(userId);
        securityLog.info("account erasure completed userId={} sessionsRevoked={}", userId, revoked);
        try {
            broadcaster.publish(RealtimeEvent.toUser(RealtimeEventType.SESSION_REVOKED,
                    Map.of("reason", "ACCOUNT_DELETED"), userId));
        } catch (RuntimeException ex) {
            // The account is already gone; a failed notification is not worth failing the request.
            log.warn("could not notify devices about account deletion userId={}", userId, ex);
        }
    }
}
