import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { conversationsApi } from '../api/conversations.js';
import { useNotificationStore } from '../state/notifications.js';
import { queryKeys } from './queryKeys.js';

/**
 * Conversation list, cursor paginated.
 *
 * The sidebar must never reorder under the user's cursor mid-click, so ordering comes from the server
 * (`last_activity_at`) and new activity is applied on the next fetch — the visible list is stable
 * between refetches, which is what makes clicking reliable.
 */
export function useConversations() {
  return useInfiniteQuery({
    queryKey: queryKeys.conversations.list(),
    queryFn: ({ pageParam }) => conversationsApi.list(pageParam, 30),
    initialPageParam: undefined,
    getNextPageParam: (lastPage) => (lastPage?.hasMore ? lastPage.nextCursor : undefined),
    staleTime: 15_000,
  });
}

export function useConversation(conversationId) {
  return useQuery({
    queryKey: queryKeys.conversations.detail(conversationId),
    queryFn: () => conversationsApi.get(conversationId),
    enabled: Boolean(conversationId),
  });
}

export function useCreateConversation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (peerUserId) => conversationsApi.create(peerUserId),
    onSuccess: (result) => {
      // The envelope distinguishes "created" (201) from "already existed" (200); the UI treats both as
      // success but only one deserves the "conversation started" wording.
      const conversation = result?.conversation || result;
      if (conversation?.id) {
        // Seed the detail cache so the new thread renders immediately instead of flashing a spinner.
        queryClient.setQueryData(queryKeys.conversations.detail(conversation.id), conversation);
      }
      queryClient.invalidateQueries({ queryKey: queryKeys.conversations.all });
      return { conversation, created: Boolean(result?.created) };
    },
  });
}

export function useConversationActions(conversationId) {
  const queryClient = useQueryClient();
  const pushToast = useNotificationStore((state) => state.pushToast);

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: queryKeys.conversations.all });
    queryClient.invalidateQueries({ queryKey: queryKeys.conversations.detail(conversationId) });
  };

  const setMuted = useMutation({
    mutationFn: (muted) => conversationsApi.mute(conversationId, muted),
    onSuccess: invalidate,
    onError: (error) =>
      pushToast({ title: 'Could not update', body: error.message, tone: 'warning' }),
  });

  const setArchived = useMutation({
    mutationFn: (archived) => conversationsApi.archive(conversationId, archived),
    onSuccess: invalidate,
    onError: (error) =>
      pushToast({ title: 'Could not update', body: error.message, tone: 'warning' }),
  });

  const remove = useMutation({
    mutationFn: () => conversationsApi.remove(conversationId),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: queryKeys.messages.list(conversationId) });
      invalidate();
    },
    onError: (error) =>
      pushToast({ title: 'Could not delete conversation', body: error.message, tone: 'warning' }),
  });

  return { setMuted, setArchived, remove };
}
