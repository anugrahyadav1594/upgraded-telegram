import { useEffect, useMemo, useRef } from 'react';

import { MessageBubble } from './MessageBubble.jsx';
import { EmptyState } from './EmptyState.jsx';
import { LoadingBlock } from './Spinner.jsx';
import { Icon } from './Icon.jsx';
import { useInfiniteScroll } from '../hooks/useInfiniteScroll.js';
import { EMPTY_QUEUE, useOutboxStore } from '../state/outbox.js';
import { dayLabel } from '../utils/time.js';

/**
 * The message timeline.
 *
 * Two behaviours here are worth more than they look:
 *
 *  * **Stick to the bottom.** New messages scroll into view only when the reader is already near the
 *    bottom. Jumping the viewport while somebody is reading history is the most complained-about bug in
 *    chat clients.
 *  * **Read boundaries are explicit.** Read receipts are sent for the newest message actually visible,
 *    not for every message rendered, so a background tab or a partially scrolled view does not claim
 *    the user has read something they have not.
 */
export function MessageList({
  conversationId,
  messages,
  isLoading,
  isError,
  error,
  hasNextPage,
  isFetchingNextPage,
  fetchNextPage,
  currentUserId,
  peer,
  onRetry,
  onEdit,
  onDelete,
  onReact,
  onReport,
  onRead,
  editingMessageId,
}) {
  const listRef = useRef(null);
  const bottomRef = useRef(null);
  const stickToBottomRef = useRef(true);

  // `?? EMPTY_QUEUE` rather than `|| []`: the fallback must keep a stable identity (see state/outbox.js).
  const optimisticEntries = useOutboxStore(
    (state) => state.byConversation[conversationId] ?? EMPTY_QUEUE,
  );
  const topSentinel = useInfiniteScroll(() => {
    if (hasNextPage && !isFetchingNextPage) fetchNextPage();
  }, { enabled: Boolean(hasNextPage), rootMargin: '300px' });

  useEffect(() => {
    stickToBottomRef.current = true;
  }, [conversationId]);

  useEffect(() => {
    if (stickToBottomRef.current) {
      bottomRef.current?.scrollIntoView({ block: 'end' });
    }
  }, [messages.length, optimisticEntries.length]);

  // The newest message the reader could actually see, derived rather than stored: a read receipt is
  // sent only for a message that is on screen. Nothing to keep in state, so nothing to fall out of sync.
  const newestIncoming = useMemo(() => {
    for (let index = messages.length - 1; index >= 0; index -= 1) {
      if (messages[index].senderId !== currentUserId) return messages[index];
    }
    return null;
  }, [messages, currentUserId]);

  useEffect(() => {
    if (newestIncoming && document.visibilityState === 'visible') {
      onRead?.(newestIncoming.id);
    }
  }, [newestIncoming, onRead]);

  // Day dividers are derived, not tracked: comparing each message's day against its predecessor keeps
  // the render function free of mutable state that could drift between passes.
  const rows = useMemo(() => {
    const days = messages.map((message) => dayLabel(message.createdAt));
    return messages.map((message, index) => ({
      message,
      divider: days[index] && days[index] !== days[index - 1] ? days[index] : null,
    }));
  }, [messages]);

  function handleScroll() {
    const node = listRef.current;
    if (!node) return;
    stickToBottomRef.current = node.scrollHeight - node.scrollTop - node.clientHeight < 120;
  }

  if (isLoading) return <LoadingBlock label="Loading messages…" />;

  if (isError) {
    return (
      <EmptyState
        icon="alert"
        title="Couldn’t load this conversation"
        description={error?.message || 'The messages could not be fetched.'}
        action={
          <button type="button" className="btn btn--secondary" onClick={() => window.location.reload()}>
            <Icon name="refresh" size={16} /> Try again
          </button>
        }
      />
    );
  }

  if (messages.length === 0 && optimisticEntries.length === 0) {
    return (
      <EmptyState
        icon="chat"
        title="No messages yet"
        description={`Say hello to ${peer?.displayName || 'them'} — messages are end-to-end encrypted between the two of you… actually, not yet: see the roadmap.`}
      />
    );
  }

  return (
    <div
      className="message-list"
      ref={listRef}
      onScroll={handleScroll}
      role="log"
      aria-live="polite"
      aria-relevant="additions"
      tabIndex={0}
      aria-label="Messages"
    >
      <div ref={topSentinel} aria-hidden="true" />
      {isFetchingNextPage ? <LoadingBlock label="Loading earlier messages…" /> : null}

      {rows.map(({ message, divider }) => {
        return (
          <div key={message.id} style={{ display: 'contents' }}>
            {divider ? <div className="day-divider">{divider}</div> : null}
            <MessageBubble
              message={message}
              currentUserId={currentUserId}
              peer={peer}
              editing={editingMessageId === message.id}
              onEdit={onEdit}
              onDelete={onDelete}
              onReact={onReact}
              onReport={onReport}
            />
          </div>
        );
      })}

      {optimisticEntries.map((entry) => (
        <MessageBubble
          key={entry.clientMessageId}
          message={{ ...entry, createdAt: entry.createdAt, reactions: [] }}
          optimistic={entry}
          currentUserId={currentUserId}
          peer={peer}
          onRetry={onRetry}
        />
      ))}

      <div ref={bottomRef} />
    </div>
  );
}

export default MessageList;
