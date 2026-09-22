package com.realtimechat.common.realtime;

/** Redis Pub/Sub channel names for cross-instance realtime fan-out. */
public final class RealtimeChannels {

    /** Message lifecycle and conversation events. */
    public static final String CHAT_EVENTS = "chat:events";

    /** Presence transitions (kept separate so presence storms cannot delay message delivery). */
    public static final String PRESENCE_EVENTS = "presence:events";

    private RealtimeChannels() {
    }
}
