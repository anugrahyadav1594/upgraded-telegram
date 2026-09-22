package com.realtimechat.common.realtime;

/**
 * The complete catalogue of server → client realtime events.
 *
 * <p>The wire name is a stable part of the public protocol
 * (see {@code docs/websocket-protocol.md §2.2}); the target queue decides whether the frame goes to
 * {@code /user/queue/messages} (durable message lifecycle) or {@code /user/queue/events}
 * (ephemeral state and notifications). Splitting the two keeps a busy chat from interleaving with
 * presence noise and lets a client subscribe to only what it renders.
 */
public enum RealtimeEventType {

    MESSAGE_ACK("message.ack", Target.MESSAGES),
    MESSAGE_NEW("message.new", Target.MESSAGES),
    MESSAGE_UPDATED("message.updated", Target.MESSAGES),
    MESSAGE_DELETED("message.deleted", Target.MESSAGES),
    MESSAGE_DELIVERED("message.delivered", Target.MESSAGES),
    MESSAGE_READ_UPDATED("message.read.updated", Target.MESSAGES),
    MESSAGE_REACTION_UPDATED("message.reaction.updated", Target.MESSAGES),
    MESSAGE_FAILED("message.failed", Target.ERRORS),

    TYPING_STARTED("typing.started", Target.EVENTS),
    TYPING_STOPPED("typing.stopped", Target.EVENTS),
    PRESENCE_UPDATED("presence.updated", Target.EVENTS),
    CONVERSATION_CREATED("conversation.created", Target.EVENTS),
    CONVERSATION_UPDATED("conversation.updated", Target.EVENTS),
    NOTIFICATION_NEW("notification.new", Target.EVENTS),
    SESSION_REVOKED("session.revoked", Target.EVENTS);

    private final String wireName;
    private final Target target;

    RealtimeEventType(String wireName, Target target) {
        this.wireName = wireName;
        this.target = target;
    }

    public String wireName() {
        return wireName;
    }

    public Target target() {
        return target;
    }

    /** Destination queue suffix for {@code convertAndSendToUser}. */
    public enum Target {
        MESSAGES("/queue/messages"),
        EVENTS("/queue/events"),
        ERRORS("/queue/errors");

        private final String destination;

        Target(String destination) {
            this.destination = destination;
        }

        public String destination() {
            return destination;
        }
    }
}
