import { create } from 'zustand';
import { newUuid } from '../utils/ids.js';

/**
 * Optimistic send queue.
 *
 * Every outgoing message exists in exactly one of five states — SENDING, SENT, DELIVERED, READ, FAILED
 * — and the UI renders straight from here. The important consequences:
 *
 *  * A message is visible the instant the user presses Enter, keyed by a client-generated
 *    `clientMessageId`, which is also the idempotency key the server deduplicates on. If the
 *    WebSocket acknowledgement is lost and the request is retried, the user still gets one bubble.
 *  * The server's acknowledgement is what moves SENDING → SENT; the client never asserts a state it
 *    has not been told about (server-authoritative state).
 *  * FAILED keeps the text so retrying is one click, and nothing is silently dropped.
 */
export const OutboxState = Object.freeze({
  SENDING: 'SENDING',
  SENT: 'SENT',
  DELIVERED: 'DELIVERED',
  READ: 'READ',
  FAILED: 'FAILED',
});

/**
 * Shared empty array.
 *
 * Components select their queue with `state.byConversation[id] ?? EMPTY_QUEUE`. That fallback must be a
 * single object with a stable identity: zustand v5 runs selectors through `useSyncExternalStore`, which
 * re-renders whenever the selected value differs by reference. `|| []` would allocate a new array on
 * every call and re-render forever — the classic "getSnapshot should be cached" loop.
 */
export const EMPTY_QUEUE = Object.freeze([]);

const RANK = {
  [OutboxState.SENDING]: 0,
  [OutboxState.SENT]: 1,
  [OutboxState.DELIVERED]: 2,
  [OutboxState.READ]: 3,
  [OutboxState.FAILED]: -1,
};

function rankOf(state) {
  return RANK[state] ?? -1;
}

export const useOutboxStore = create((set, get) => ({
  /** conversationId → ordered array of optimistic entries */
  byConversation: Object.freeze({}),

  enqueue(conversationId, { content, type = 'TEXT' }) {
    const clientMessageId = newUuid();
    const entry = {
      clientMessageId,
      conversationId,
      content,
      type,
      state: OutboxState.SENDING,
      createdAt: new Date().toISOString(),
      attempts: 0,
      error: null,
    };
    set((store) => ({
      byConversation: {
        ...store.byConversation,
        [conversationId]: [...(store.byConversation[conversationId] || []), entry],
      },
    }));
    return entry;
  },

  /** Marks a queued message as acknowledged, keeping the highest state ever seen. */
  acknowledge(conversationId, clientMessageId, state = OutboxState.SENT, serverMessage = null) {
    set((store) => {
      const entries = store.byConversation[conversationId];
      if (!entries) return store;
      return {
        byConversation: {
          ...store.byConversation,
          [conversationId]: entries.map((entry) => {
            if (entry.clientMessageId !== clientMessageId) return entry;
            const nextState = rankOf(state) > rankOf(entry.state) ? state : entry.state;
            return {
              ...entry,
              state: nextState,
              error: null,
              server: serverMessage || entry.server,
            };
          }),
        },
      };
    });
  },

  /**
   * Promotes acknowledged messages to DELIVERED/READ.
   *
   * Receipts arrive as a watermark (`lastMessageId`, not a list of ids), so the only message whose new
   * state is unambiguous from the frame alone is the watermark message itself. Everything older is left
   * to the next history fetch, which returns each message's authoritative `receipt` field — guessing
   * from a watermark would mean inventing ordering the client does not have.
   */
  applyReceipt(conversationId, { deliveredId, readId }) {
    set((store) => {
      const entries = store.byConversation[conversationId];
      if (!entries) return store;
      return {
        byConversation: {
          ...store.byConversation,
          [conversationId]: entries.map((entry) => {
            if (entry.state === OutboxState.FAILED || entry.state === OutboxState.SENDING) return entry;

            // Receipts are monotonic in the same way the database's are: DELIVERED arriving after READ
            // (frames can be reordered on reconnect) must not demote the message.
            const candidates = [];
            if (readId && entry.server?.id === readId) candidates.push(OutboxState.READ);
            if (deliveredId && entry.server?.id === deliveredId) candidates.push(OutboxState.DELIVERED);
            const promoted = candidates.find((state) => rankOf(state) > rankOf(entry.state));
            return promoted ? { ...entry, state: promoted } : entry;
          }),
        },
      };
    });
  },

  markFailed(conversationId, clientMessageId, message) {
    set((store) => {
      const entries = store.byConversation[conversationId];
      if (!entries) return store;
      return {
        byConversation: {
          ...store.byConversation,
          [conversationId]: entries.map((entry) =>
            entry.clientMessageId === clientMessageId
              ? { ...entry, state: OutboxState.FAILED, error: message || 'Could not send', attempts: entry.attempts + 1 }
              : entry,
          ),
        },
      };
    });
  },

  /** Re-queues a failed message under the *same* clientMessageId (idempotency preserved). */
  retry(conversationId, clientMessageId) {
    set((store) => {
      const entries = store.byConversation[conversationId];
      if (!entries) return store;
      return {
        byConversation: {
          ...store.byConversation,
          [conversationId]: entries.map((entry) =>
            entry.clientMessageId === clientMessageId
              ? { ...entry, state: OutboxState.SENDING, error: null }
              : entry,
          ),
        },
      };
    });
    const entry = (get().byConversation[conversationId] || []).find(
      (item) => item.clientMessageId === clientMessageId,
    );
    return entry || null;
  },

  /**
   * Drops entries the server has already confirmed.
   *
   * Called after a history page is fetched: anything present server-side now renders from the
   * authoritative list (with its real receipt), so keeping a duplicate optimistic copy would show the
   * message twice. Messages still SENDING or FAILED are kept — those are the ones the user must see.
   */
  reconcile(conversationId, serverMessages = []) {
    set((store) => {
      const entries = store.byConversation[conversationId];
      if (!entries) return store;
      const serverIds = new Set(serverMessages.map((message) => message?.id).filter(Boolean));
      const serverClientIds = new Set(
        serverMessages.map((message) => message?.clientMessageId).filter(Boolean),
      );
      const remaining = entries.filter((entry) => {
        if (entry.state === OutboxState.SENDING || entry.state === OutboxState.FAILED) return true;
        if (entry.server?.id && serverIds.has(entry.server.id)) return false;
        return !serverClientIds.has(entry.clientMessageId);
      });
      return { byConversation: { ...store.byConversation, [conversationId]: remaining } };
    });
  },

  remove(conversationId, clientMessageId) {
    set((store) => {
      const entries = store.byConversation[conversationId] || [];
      return {
        byConversation: {
          ...store.byConversation,
          [conversationId]: entries.filter((entry) => entry.clientMessageId !== clientMessageId),
        },
      };
    });
  },

  clearConversation(conversationId) {
    set((store) => {
      const next = { ...store.byConversation };
      delete next[conversationId];
      return { byConversation: next };
    });
  },

  pendingFor(conversationId) {
    return get().byConversation[conversationId] || [];
  },
}));
