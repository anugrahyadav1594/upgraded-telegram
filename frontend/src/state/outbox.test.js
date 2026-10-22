import { beforeEach, describe, expect, it } from 'vitest';

import { OutboxState, useOutboxStore } from './outbox.js';

/**
 * The outbox is the difference between "my message vanished" and "my message is waiting": these tests pin
 * the state machine, including the rule that a late frame can never move a message backwards.
 */
describe('outbox', () => {
  const conversationId = 'c1';

  beforeEach(() => {
    useOutboxStore.setState({ byConversation: {} });
  });

  it('queues a message with a client id used as the idempotency key', () => {
    const entry = useOutboxStore.getState().enqueue(conversationId, { content: 'hello' });

    expect(entry.clientMessageId).toMatch(/^[0-9a-f-]{36}$/);
    expect(entry.state).toBe(OutboxState.SENDING);
    expect(useOutboxStore.getState().pendingFor(conversationId)).toHaveLength(1);
  });

  it('moves SENDING → SENT → DELIVERED → READ and never backwards', () => {
    const entry = useOutboxStore.getState().enqueue(conversationId, { content: 'hello' });
    const { acknowledge, applyReceipt } = useOutboxStore.getState();

    acknowledge(conversationId, entry.clientMessageId, OutboxState.SENT, { id: 'm1' });
    acknowledge(conversationId, entry.clientMessageId, OutboxState.READ, { id: 'm1' });
    expect(useOutboxStore.getState().pendingFor(conversationId)[0].state).toBe(OutboxState.READ);

    // A stale DELIVERED frame arriving after READ must not regress the receipt.
    acknowledge(conversationId, entry.clientMessageId, OutboxState.DELIVERED, { id: 'm1' });
    expect(useOutboxStore.getState().pendingFor(conversationId)[0].state).toBe(OutboxState.READ);

    applyReceipt(conversationId, { deliveredId: 'm1' });
    expect(useOutboxStore.getState().pendingFor(conversationId)[0].state).toBe(OutboxState.READ);
  });

  it('keeps the text when a send fails so it can be retried', () => {
    const entry = useOutboxStore.getState().enqueue(conversationId, { content: 'will fail' });
    useOutboxStore.getState().markFailed(conversationId, entry.clientMessageId, 'Network');

    const failed = useOutboxStore.getState().pendingFor(conversationId)[0];
    expect(failed.state).toBe(OutboxState.FAILED);
    expect(failed.content).toBe('will fail');

    useOutboxStore.getState().retry(conversationId, entry.clientMessageId);
    expect(useOutboxStore.getState().pendingFor(conversationId)[0].state).toBe(OutboxState.SENDING);
  });

  it('reconciles away confirmed messages but keeps failed ones', () => {
    const store = useOutboxStore.getState();
    const confirmed = store.enqueue(conversationId, { content: 'sent' });
    const failed = store.enqueue(conversationId, { content: 'failed' });

    store.acknowledge(conversationId, confirmed.clientMessageId, OutboxState.SENT, { id: 'server-1' });
    store.markFailed(conversationId, failed.clientMessageId, 'boom');

    useOutboxStore
      .getState()
      .reconcile(conversationId, [{ id: 'server-1', clientMessageId: confirmed.clientMessageId }]);

    const remaining = useOutboxStore.getState().pendingFor(conversationId);
    expect(remaining).toHaveLength(1);
    expect(remaining[0].clientMessageId).toBe(failed.clientMessageId);
  });
});
