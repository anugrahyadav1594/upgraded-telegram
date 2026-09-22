import { create } from 'zustand';
import { ConnectionState } from '../websocket/stompClient.js';

/**
 * Connection health, typing indicators and presence — the ephemeral half of the app.
 *
 * Nothing here is persisted: typing indicators and presence are derived from Redis-backed server state
 * and must never be written to the database or to localStorage, so this store is intentionally
 * in-memory only and is emptied on sign-out.
 */
export const useRealtimeStore = create((set, get) => ({
  connection: ConnectionState.IDLE,
  lastConnectedAt: null,
  lastDisconnectedAt: null,
  reconnectAttempt: 0,

  /** conversationId → Map(userId → {expiresAt}) for typing; entries expire client-side too. */
  typing: {},
  presence: {},
  unreadByConversation: {},
  missedResyncDone: false,

  setConnection(state, detail = {}) {
    set((store) => ({
      connection: state,
      reconnectAttempt: state === ConnectionState.RECONNECTING ? detail.attempt || 0 : 0,
      lastConnectedAt: state === ConnectionState.CONNECTED ? new Date().toISOString() : store.lastConnectedAt,
      lastDisconnectedAt:
        state === ConnectionState.DISCONNECTED || state === ConnectionState.FAILED
          ? new Date().toISOString()
          : store.lastDisconnectedAt,
    }));
  },

  markResynced() {
    set({ missedResyncDone: true });
  },

  setPresence(userId, presence) {
    set((store) => ({ presence: { ...store.presence, [userId]: presence } }));
  },

  setPresenceBatch(entries) {
    if (!entries || entries.length === 0) return;
    set((store) => {
      const next = { ...store.presence };
      entries.forEach((entry) => {
        if (entry?.userId) next[entry.userId] = entry;
      });
      return { presence: next };
    });
  },

  setTyping(conversationId, userId, isTyping) {
    set((store) => {
      const forConversation = { ...(store.typing[conversationId] || {}) };
      if (isTyping) {
        forConversation[userId] = { expiresAt: Date.now() + 6000 };
      } else {
        delete forConversation[userId];
      }
      return { typing: { ...store.typing, [conversationId]: forConversation } };
    });
  },

  /** Drops typing entries that the server stopped refreshing — a closed tab must not look like typing. */
  pruneTyping() {
    const now = Date.now();
    set((store) => {
      let changed = false;
      const next = {};
      Object.entries(store.typing).forEach(([conversationId, users]) => {
        const kept = {};
        Object.entries(users).forEach(([userId, value]) => {
          if (value.expiresAt > now) kept[userId] = value;
          else changed = true;
        });
        next[conversationId] = kept;
      });
      return changed ? { typing: next } : {};
    });
  },

  setUnread(conversationId, count) {
    set((store) => ({
      unreadByConversation: { ...store.unreadByConversation, [conversationId]: Math.max(0, count) },
    }));
  },

  incrementUnread(conversationId) {
    set((store) => ({
      unreadByConversation: {
        ...store.unreadByConversation,
        [conversationId]: (store.unreadByConversation[conversationId] || 0) + 1,
      },
    }));
  },

  totalUnread() {
    return Object.values(get().unreadByConversation).reduce((sum, value) => sum + value, 0);
  },

  clearUnread(conversationId) {
    set((store) => {
      const next = { ...store.unreadByConversation };
      delete next[conversationId];
      return { unreadByConversation: next };
    });
  },

  reset() {
    set({
      connection: ConnectionState.IDLE,
      typing: {},
      presence: {},
      unreadByConversation: {},
      reconnectAttempt: 0,
      missedResyncDone: false,
    });
  },
}));
