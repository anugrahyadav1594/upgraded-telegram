import { useEffect } from 'react';

import { ConversationListItem } from './ConversationListItem.jsx';
import { EmptyState } from './EmptyState.jsx';
import { LoadingBlock, SkeletonRows } from './Spinner.jsx';
import { useInfiniteScroll } from '../hooks/useInfiniteScroll.js';
import { useRealtimeStore } from '../state/realtime.js';

/**
 * The conversation list.
 *
 * Loading, empty and error states are all first-class: a chat app whose sidebar says nothing when the
 * network is down is indistinguishable from one with no conversations.
 */
export function ConversationList({
  conversations,
  isLoading,
  isError,
  error,
  hasNextPage,
  isFetchingNextPage,
  fetchNextPage,
  onSelect,
  selectedId,
  currentUserId,
}) {
  const sentinelRef = useInfiniteScroll(() => {
    if (hasNextPage && !isFetchingNextPage) fetchNextPage();
  }, { enabled: Boolean(hasNextPage) });

  // Keep the DOM in sync with the unread counts the server returned.
  const setUnread = useRealtimeStore((state) => state.setUnread);
  useEffect(() => {
    conversations.forEach((conversation) => {
      setUnread(conversation.id, conversation.unreadCount || 0);
    });
  }, [conversations, setUnread]);

  if (isLoading) return <SkeletonRows rows={6} />;

  if (isError) {
    return (
      <EmptyState
        icon="alert"
        title="Couldn’t load conversations"
        description={error?.message || 'Something went wrong while fetching your chats.'}
      />
    );
  }

  if (conversations.length === 0) {
    return (
      <EmptyState
        icon="chat"
        title="No conversations yet"
        description="Search for someone by username to start your first chat."
      />
    );
  }

  return (
    <ul className="conversation-list">
      {conversations.map((conversation) => (
        <ConversationListItem
          key={conversation.id}
          conversation={conversation}
          currentUserId={currentUserId}
          selected={conversation.id === selectedId}
          onSelect={onSelect}
        />
      ))}
      <li ref={sentinelRef} aria-hidden="true" />
      {isFetchingNextPage ? <LoadingBlock label="Loading more…" /> : null}
    </ul>
  );
}

export default ConversationList;
