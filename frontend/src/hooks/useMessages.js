import { useCallback, useEffect, useMemo, useRef } from 'react';
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';

import { messagesApi } from '../api/messages.js';
import { chatCommands } from '../websocket/commands.js';
import { ApiError } from '../api/client.js';
import { useOutboxStore, OutboxState } from '../state/outbox.js';
import { useRealtimeStore } from '../state/realtime.js';
import { useNotificationStore } from '../state/notifications.js';
import { queryKeys } from './queryKeys.js';

const PAGE_SIZE = 50;

/**
 * Message history, cursor paginated.
 *
 * `before` walks backwards through time (the common case: scrolling up), which is why the pages are
 * reversed when flattened — the API returns newest-first pages, the UI renders oldest-first. Offsets are
 * never used: a conversation that is being written to would shift under them and duplicate messages.
 */
export function useMessageHistory(conversationId, { enabled = true } = {}) {
  const queryClient = useQueryClient();
  const reconcile = useOutboxStore((state) => state.reconcile);
  const connection = useRealtimeStore((state) => state.connection);

  const query = useInfiniteQuery({
    queryKey: queryKeys.messages.list(conversationId),
    queryFn: async ({ pageParam }) => {
      const page = await messagesApi.history(conversationId, { before: pageParam, limit: PAGE_SIZE });
      return page;
    },
    initialPageParam: undefined,
    getNextPageParam: (lastPage) => (lastPage?.hasMore ? lastPage.nextCursor : undefined),
    enabled: Boolean(conversationId) && enabled,
    staleTime: 5_000,
  });

  // Once the server has confirmed a message, the optimistic copy must disappear or it renders twice.
  useEffect(() => {
    if (!query.data || !conversationId) return;
    const serverMessages = query.data.pages.flatMap((page) => page.items || []);
    reconcile(conversationId, serverMessages);
  }, [query.data, conversationId, reconcile]);

  // On every (re)connection, ask for anything newer than what we hold. This is the documented resync
  // path and the reason a dropped socket never silently loses messages.
  useEffect(() => {
    if (connection !== 'CONNECTED' || !conversationId) return;
    const known = queryClient.getQueryData(queryKeys.messages.list(conversationId));
    if (!known) return;
    const newest = known.pages?.[0]?.items?.[0];
    if (!newest?.sequence) return;

    let cancelled = false;
    messagesApi
      .resync(conversationId, newest.sequence)
      .then((page) => {
        if (cancelled || !page?.items?.length) return;
        queryClient.invalidateQueries({ queryKey: queryKeys.messages.list(conversationId) });
      })
      .catch(() => {
        // Resync failures are non-fatal: the next reconnect or refetch tries again.
      });

    return () => {
      cancelled = true;
    };
  }, [connection, conversationId, queryClient]);

  const messages = useMemo(() => {
    if (!query.data) return [];
    // Each page is newest-first; flatten then reverse so the array is oldest → newest overall.
    return query.data.pages.flatMap((page) => page.items || []).slice().reverse();
  }, [query.data]);

  return { ...query, messages };
}

/**
 * Sending.
 *
 * The message appears immediately (SENDING), then travels over the socket when it is up and over REST
 * when it is not — the same idempotency key in both cases, so a duplicate delivery is impossible.
 * A REST failure marks the bubble FAILED rather than dropping it.
 */
export function useSendMessage(conversationId) {
  const queryClient = useQueryClient();
  const enqueue = useOutboxStore((state) => state.enqueue);
  const acknowledge = useOutboxStore((state) => state.acknowledge);
  const markFailed = useOutboxStore((state) => state.markFailed);

  return useMutation({
    mutationFn: async ({ content }) => {
      const entry = enqueue(conversationId, { content });
      const published = chatCommands.sendMessage({
        conversationId,
        clientMessageId: entry.clientMessageId,
        content,
        messageType: 'TEXT',
      });

      if (published) {
        // The server answers with `message.ack`, handled centrally by the realtime bridge.
        return { entry, via: 'websocket' };
      }

      try {
        const message = await messagesApi.send(conversationId, {
          content,
          clientMessageId: entry.clientMessageId,
        });
        acknowledge(conversationId, entry.clientMessageId, OutboxState.SENT, message);
        return { entry, message, via: 'rest' };
      } catch (error) {
        markFailed(conversationId, entry.clientMessageId, error.message);
        throw error;
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: queryKeys.conversations.all });
    },
  });
}

/** Retries a FAILED bubble, keeping the original clientMessageId (and therefore idempotency). */
export function useRetryMessage(conversationId) {
  const retry = useOutboxStore((state) => state.retry);
  const acknowledge = useOutboxStore((state) => state.acknowledge);
  const markFailed = useOutboxStore((state) => state.markFailed);
  const pushToast = useNotificationStore((state) => state.pushToast);

  return useCallback(
    async (clientMessageId) => {
      const entry = retry(conversationId, clientMessageId);
      if (!entry) return;
      try {
        const message = await messagesApi.send(conversationId, {
          content: entry.content,
          clientMessageId: entry.clientMessageId,
        });
        acknowledge(conversationId, entry.clientMessageId, OutboxState.SENT, message);
      } catch (error) {
        markFailed(conversationId, entry.clientMessageId, error.message);
        if (error instanceof ApiError && error.isRateLimited) {
          pushToast({ title: 'Slow down', body: 'You are sending messages very quickly.', tone: 'warning' });
        }
      }
    },
    [conversationId, retry, acknowledge, markFailed, pushToast],
  );
}

export function useMessageActions() {
  const queryClient = useQueryClient();
  const pushToast = useNotificationStore((state) => state.pushToast);

  const invalidate = (conversationId) => {
    queryClient.invalidateQueries({ queryKey: queryKeys.messages.list(conversationId) });
  };

  const edit = useMutation({
    mutationFn: ({ messageId, content }) => messagesApi.edit(messageId, content),
    onSuccess: (message) => invalidate(message?.conversationId),
    onError: (error) => pushToast({ title: 'Could not edit', body: error.message, tone: 'warning' }),
  });

  const remove = useMutation({
    mutationFn: ({ messageId, conversationId }) => messagesApi.remove(messageId).then(() => conversationId),
    onSuccess: (conversationId) => invalidate(conversationId),
    onError: (error) => pushToast({ title: 'Could not delete', body: error.message, tone: 'warning' }),
  });

  const toggleReaction = useMutation({
    mutationFn: ({ messageId, emoji, reacted }) =>
      reacted ? messagesApi.unreact(messageId, emoji) : messagesApi.react(messageId, emoji),
    onSuccess: (message) => invalidate(message?.conversationId),
    onError: (error) => pushToast({ title: 'Could not react', body: error.message, tone: 'warning' }),
  });

  return { edit, remove, toggleReaction };
}

/**
 * Marks a conversation read up to the newest visible message.
 *
 * Deliberately throttled: the socket path is rate limited at 30/min, and scrolling a long thread can
 * produce far more than that. The server is authoritative either way, so a skipped send costs nothing.
 */
export function useMarkConversationRead(conversationId) {
  const queryClient = useQueryClient();
  const lastSentRef = useRef(0);
  const clearUnread = useRealtimeStore((state) => state.clearUnread);

  return useCallback(
    async (messageId, { force = false } = {}) => {
      if (!conversationId || !messageId) return;
      const now = Date.now();
      if (!force && now - lastSentRef.current < 1500) return;
      lastSentRef.current = now;

      const published = chatCommands.markRead({ conversationId, lastMessageId: messageId });
      if (!published) {
        try {
          await messagesApi.markRead(messageId);
        } catch {
          // Reading is best-effort; the unread badge simply stays until the next successful attempt.
          return;
        }
      }
      clearUnread(conversationId);
      queryClient.invalidateQueries({ queryKey: queryKeys.conversations.all });
    },
    [conversationId, clearUnread, queryClient],
  );
}
