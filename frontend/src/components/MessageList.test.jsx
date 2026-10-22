import { beforeEach, describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';

import { MessageList } from './MessageList.jsx';
import { EMPTY_QUEUE, useOutboxStore } from '../state/outbox.js';

/**
 * Regression guard for a real crash.
 *
 * `MessageList` selects its queue with `state.byConversation[id] ?? EMPTY_QUEUE`. With the obvious
 * `|| []` fallback the selector returns a brand-new array on every call; zustand feeds selectors through
 * `useSyncExternalStore`, React compares snapshots by reference, and the component re-renders forever
 * until it dies with "Maximum update depth exceeded". Opening any conversation with an empty outbox —
 * which is every conversation, most of the time — blanked the thread behind the error boundary.
 *
 * The unit test asserts the invariant directly (same reference across calls); the render tests are what
 * would have caught the crash in the first place.
 */
describe('MessageList outbox selector', () => {
  const conversationId = 'c1';
  const baseProps = {
    conversationId,
    messages: [],
    isLoading: false,
    isError: false,
    error: null,
    hasNextPage: false,
    isFetchingNextPage: false,
    fetchNextPage: () => {},
    currentUserId: 'u1',
    peer: { id: 'u2', username: 'ghost', displayName: 'Ghost' },
    editingMessageId: null,
  };

  beforeEach(() => {
    useOutboxStore.setState({ byConversation: {} });
  });

  it('returns the same empty queue every time, so React sees a stable snapshot', () => {
    const select = (state) => state.byConversation[conversationId] ?? EMPTY_QUEUE;

    expect(select(useOutboxStore.getState())).toBe(EMPTY_QUEUE);
    expect(select(useOutboxStore.getState())).toBe(select(useOutboxStore.getState()));
  });

  it('keeps the fallback immutable', () => {
    expect(Object.isFrozen(EMPTY_QUEUE)).toBe(true);
  });

  it('renders an empty conversation without looping', () => {
    render(<MessageList {...baseProps} />);

    expect(screen.getByText(/no messages yet/i)).toBeInTheDocument();
  });

  it('renders a conversation with history while the outbox is empty', () => {
    render(
      <MessageList
        {...baseProps}
        messages={[
          {
            id: 'm1',
            senderId: 'u2',
            content: 'are you there?',
            createdAt: new Date().toISOString(),
            reactions: [],
          },
        ]}
      />,
    );

    expect(screen.getByRole('log', { name: /messages/i })).toBeInTheDocument();
    expect(screen.getByText('are you there?')).toBeInTheDocument();
  });

  it('shows a queued message that the server has not acknowledged yet', () => {
    useOutboxStore.getState().enqueue(conversationId, { content: 'sending now' });

    render(<MessageList {...baseProps} />);

    expect(screen.getByText('sending now')).toBeInTheDocument();
  });
});
