import { useEffect, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';

import { realtimeClient, ConnectionState } from '../websocket/stompClient.js';
import { useSessionStore } from '../state/session.js';
import { useRealtimeStore } from '../state/realtime.js';
import { useNotificationStore } from '../state/notifications.js';
import { useOutboxStore, OutboxState } from '../state/outbox.js';
import { queryKeys } from './queryKeys.js';

/**
 * Bridges the socket to application state.
 *
 * The socket is a notification channel, not a store: every frame either updates ephemeral state
 * (typing, presence, connection) or invalidates server state that React Query will refetch. That split
 * is what keeps the client honest — a missed frame degrades freshness, never correctness.
 */
export function useRealtime({ onSessionRevoked } = {}) {
  const queryClient = useQueryClient();
  const status = useSessionStore((state) => state.status);
  const clientRef = useRef(null);

  const onSessionRevokedRef = useRef(onSessionRevoked);

  useEffect(() => {
    onSessionRevokedRef.current = onSessionRevoked;
  });

  useEffect(() => {
    if (status !== 'AUTHENTICATED') return undefined;

    const store = useRealtimeStore.getState();
    const notifications = useNotificationStore.getState();

    const client = realtimeClient({
      onStateChange: (state, detail) => {
        useRealtimeStore.getState().setConnection(state, detail);
      },

      onResync: () => {
        // After a reconnect the client may have missed frames. Pull authoritative state:
        // conversations first (so unread badges are right), then the open conversation's messages.
        queryClient.invalidateQueries({ queryKey: queryKeys.conversations.all });
        queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all });
        useRealtimeStore.getState().markResynced();
      },

      onMessage: (frame) => {
        const type = frame?.type;
        const payload = frame?.payload || frame || {};
        const conversationId = payload.conversationId || payload.message?.conversationId;

        switch (type) {
          case 'message.new':
            queryClient.invalidateQueries({ queryKey: queryKeys.messages.list(conversationId) });
            queryClient.invalidateQueries({ queryKey: queryKeys.conversations.all });
            break;
          case 'message.ack': {
            // Our own message is now durable: move it SENT and attach the server id/sequence so later
            // receipts (delivered/read) can resolve against a real message.
            const outbox = useOutboxStore.getState();
            outbox.acknowledge(conversationId, payload.clientMessageId, OutboxState.SENT, payload.message);
            break;
          }
          case 'message.delivered':
            useOutboxStore.getState().applyReceipt(conversationId, { deliveredId: payload.lastMessageId });
            queryClient.invalidateQueries({ queryKey: queryKeys.conversations.all });
            break;
          case 'message.read.updated':
            useOutboxStore.getState().applyReceipt(conversationId, { readId: payload.lastMessageId });
            queryClient.invalidateQueries({ queryKey: queryKeys.conversations.all });
            queryClient.invalidateQueries({ queryKey: queryKeys.messages.list(conversationId) });
            break;
          case 'message.updated':
          case 'message.deleted':
          case 'message.reaction.updated':
            queryClient.invalidateQueries({ queryKey: queryKeys.messages.list(conversationId) });
            break;
          case 'message.failed': {
            const outbox = useOutboxStore.getState();
            outbox.markFailed(conversationId, payload.clientMessageId, payload.message);
            break;
          }
          case 'typing.started':
            useRealtimeStore.getState().setTyping(conversationId, payload.userId, true, payload);
            break;
          case 'typing.stopped':
            useRealtimeStore.getState().setTyping(conversationId, payload.userId, false);
            break;
          case 'conversation.created':
            queryClient.invalidateQueries({ queryKey: queryKeys.conversations.all });
            notifications.pushToast({
              title: 'New conversation',
              body: payload.conversation?.title || 'Someone started a conversation with you',
              tone: 'info',
            });
            break;
          case 'conversation.updated':
            queryClient.invalidateQueries({ queryKey: queryKeys.conversations.all });
            break;
          case 'presence.updated':
            useRealtimeStore.getState().setPresence(payload.userId, payload);
            break;
          case 'notification.new':
            notifications.receive(payload.notification || payload);
            break;
          case 'session.revoked':
            useSessionStore.getState().markSignedOut();
            onSessionRevokedRef.current?.(payload);
            break;
          default:
            break;
        }
      },

      onEvent: (frame) => {
        const payload = frame?.payload || frame;
        if (frame?.type === 'typing.started' || frame?.type === 'typing.stopped') {
          // The protocol lists typing on both `/user/queue/events` and the conversation topic; handling
          // it here as well costs nothing and means the indicator works on either route.
          useRealtimeStore
            .getState()
            .setTyping(payload.conversationId, payload.userId, frame.type === 'typing.started', payload);
          return;
        }
        if (frame?.type === 'notification.new') {
          notifications.receive(payload.notification || payload);
          return;
        }
        if (frame?.type === 'presence.updated') {
          useRealtimeStore.getState().setPresence(payload.userId, payload);
          return;
        }
        if (frame?.type === 'session.revoked') {
          useSessionStore.getState().markSignedOut();
          onSessionRevokedRef.current?.(payload);
        }
      },

      onError: (frame) => {
        const payload = frame?.payload || frame || {};
        if (payload.code === 'WS_UNAUTHORIZED') {
          useSessionStore.getState().markSignedOut();
          return;
        }
        // A failed send carries the identifiers needed to mark exactly one bubble FAILED, and to keep
        // the text so the user can retry instead of retyping.
        if (payload.clientMessageId && payload.conversationId) {
          useOutboxStore
            .getState()
            .markFailed(payload.conversationId, payload.clientMessageId, payload.message);
          return;
        }
        if (payload.code) {
          notifications.pushToast({
            title: 'Realtime problem',
            body: payload.message || 'The server rejected that action.',
            tone: 'warning',
          });
        }
      },

      onTransportError: () => {
        // Transport errors are expected during reconnects; the connection state already tells the user.
      },
    });

    clientRef.current = client;
    client.connect();

    // Typing entries expire client-side; pruning keeps a closed tab from looking like an active typist.
    const pruneTimer = window.setInterval(() => useRealtimeStore.getState().pruneTyping(), 2000);

    return () => {
      window.clearInterval(pruneTimer);
      clientRef.current = null;
      client.disconnect();
      store.reset();
    };
  }, [status, queryClient]);

  return clientRef;
}

export { chatCommands } from '../websocket/commands.js';

export const RealtimeConnectionState = ConnectionState;
