import { useEffect } from 'react';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { notificationsApi } from '../api/notifications.js';
import { useNotificationStore } from '../state/notifications.js';
import { queryKeys } from './queryKeys.js';

/** Notification centre list, newest first. */
export function useNotificationList(unreadOnly = false) {
  const setItems = useNotificationStore((state) => state.setItems);

  const query = useInfiniteQuery({
    queryKey: queryKeys.notifications.list(unreadOnly),
    queryFn: ({ pageParam }) => notificationsApi.list({ cursor: pageParam, unreadOnly, limit: 20 }),
    initialPageParam: undefined,
    getNextPageParam: (lastPage) => (lastPage?.hasMore ? lastPage.nextCursor : undefined),
  });

  useEffect(() => {
    if (query.data) setItems(query.data.pages.flatMap((page) => page.items || []));
  }, [query.data, setItems]);

  return query;
}

export function useNotificationSettings() {
  return useQuery({
    queryKey: queryKeys.notifications.settings,
    queryFn: () => notificationsApi.getSettings(),
  });
}

export function useNotificationMutations() {
  const queryClient = useQueryClient();
  const markReadLocally = useNotificationStore((state) => state.markRead);
  const setUnreadCount = useNotificationStore((state) => state.setUnreadCount);

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: queryKeys.notifications.all });
  };

  const markRead = useMutation({
    mutationFn: (ids) => notificationsApi.markRead(ids),
    onSuccess: (result) => {
      markReadLocally();
      if (typeof result?.unreadCount === 'number') setUnreadCount(result.unreadCount);
      invalidate();
    },
  });

  const remove = useMutation({
    mutationFn: (id) => notificationsApi.remove(id),
    onSuccess: invalidate,
  });

  const updateSettings = useMutation({
    mutationFn: (payload) => notificationsApi.updateSettings(payload),
    onSuccess: (settings) => queryClient.setQueryData(queryKeys.notifications.settings, settings),
  });

  return { markRead, remove, updateSettings };
}
