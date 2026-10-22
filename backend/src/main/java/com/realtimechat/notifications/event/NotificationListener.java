package com.realtimechat.notifications.event;

import com.realtimechat.messaging.event.MessageCommittedEvent;
import com.realtimechat.notifications.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Turns committed messages into notifications.
 *
 * <p>Two properties matter here and both are deliberate:
 *
 * <ol>
 *   <li><b>After commit.</b> The listener fires only once the message is durable. Notifying somebody
 *       about a message that a rollback erased would be a lie the user acts on ("I saw the
 *       notification" but nothing is there).</li>
 *   <li><b>Asynchronous, on a bounded executor.</b> Notification work — preference lookups, a row
 *       insert, a Redis publish — must never sit in the sender's request thread, and must never be
 *       able to slow down messaging under load. Failures are logged and swallowed: a lost badge is a
 *       lost convenience, whereas a failed send is lost data.</li>
 * </ol>
 */
@Component
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private final NotificationService notificationService;

    public NotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageCommitted(MessageCommittedEvent event) {
        for (UUID recipient : event.recipientIds()) {
            try {
                notificationService.createMessageNotification(recipient, event.conversationId(),
                        event.messageId(), event.senderId(), event.preview());
            } catch (RuntimeException ex) {
                log.warn("notification creation failed recipient={} message={}", recipient, event.messageId(), ex);
            }
        }
    }
}
