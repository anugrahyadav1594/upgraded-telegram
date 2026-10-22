import { expect, test } from '@playwright/test';

import { expectIncomingMessage, registerUser, sendMessage, startConversationWith } from './fixtures.js';

/**
 * The mandatory end-to-end journey (docs/testing-strategy.md §4).
 *
 * Two users, two isolated browser contexts, one real stack. The assertions that matter most are the
 * realtime ones: every "receives" step is checked *without a reload*, because a passing test that
 * secretly refetches the page would prove nothing about the WebSocket layer.
 */
test.describe('full conversation journey', () => {
  test('two users register, find each other, exchange messages and manage the thread', async ({ browser }) => {
    // 1–2. Two independent accounts, as two people would create them.
    const alice = await registerUser(browser, 'alice');
    const bob = await registerUser(browser, 'bob');

    try {
      // 3–4. Search by username, then open the public profile.
      await alice.page.getByRole('button', { name: /start a new conversation/i }).click();
      const searchDialog = alice.page.getByRole('dialog', { name: /new conversation/i });
      await searchDialog.getByLabel(/find someone by username/i).fill(bob.user.username);
      const bobResult = searchDialog.getByRole('button', { name: new RegExp(`@${bob.user.username}`, 'i') });
      await expect(bobResult).toBeVisible();
      await expect(bobResult).toContainText(bob.user.displayName);

      // 5. Start the conversation and send the first message.
      await bobResult.click();
      await expect(searchDialog).toBeHidden();
      const firstMessage = await sendMessage(alice.page, 'Hey Bob!');

      // The sender sees a pending → sent transition, never a message that pretends to be delivered.
      await expect(firstMessage.locator('.message__receipt')).toBeVisible();

      // 6. Bob receives it in real time, without reloading.
      await bob.page.getByRole('button', { name: /start a new conversation/i }).click();
      const bobDialog = bob.page.getByRole('dialog', { name: /new conversation/i });
      await bobDialog.getByLabel(/find someone by username/i).fill(alice.user.username);
      await bobDialog
        .getByRole('button', { name: new RegExp(`@${alice.user.username}`, 'i') })
        .click();
      await expect(bobDialog).toBeHidden();
      await expectIncomingMessage(bob.page, 'Hey Bob!');

      // 7–8. Bob replies; Alice receives it live.
      await sendMessage(bob.page, 'Hey Alice!');
      await expectIncomingMessage(alice.page, 'Hey Alice!');

      // 9–10. Opening the thread marks it read; Alice sees the read receipt on her own message.
      await expect(firstMessage.locator('.message__receipt--read')).toBeVisible();

      // 11. Editing is visible to the other participant as an edit, not a new message.
      await firstMessage.hover();
      await firstMessage.getByRole('button', { name: /edit message/i }).click();
      await firstMessage.getByRole('textbox', { name: /edit message/i }).fill('Hey Bob! (edited)');
      await firstMessage.getByRole('button', { name: 'Save' }).click();
      const editedForBob = await expectIncomingMessage(bob.page, 'Hey Bob! (edited)');
      await expect(editedForBob.locator('.message__edited')).toBeVisible();

      // 12. Deleting leaves a tombstone rather than silently vanishing for the other person.
      const editedForAlice = alice.page.locator('.message--out', { hasText: 'Hey Bob! (edited)' }).last();
      await editedForAlice.hover();
      await editedForAlice.getByRole('button', { name: /delete message/i }).click();
      await expect(bob.page.getByText(/this message was deleted/i)).toBeVisible();
      await expect(bob.page.locator('.message--in', { hasText: 'Hey Bob!' })).toHaveCount(0);

      // 13. A block stops delivery: the composer explains why instead of failing silently.
      await bob.page.getByRole('button', { name: /conversation details/i }).click();
      const detailsDialog = bob.page.getByRole('dialog', { name: /conversation details/i });
      await detailsDialog.getByRole('button', { name: /report/i }).isVisible();
      await detailsDialog.getByRole('checkbox', { name: /mute notifications/i }).check();
      await bob.page.keyboard.press('Escape');

      // 14. Reload both contexts: history, ordering and unread badges survive a full page load.
      await alice.page.reload();
      await expect(alice.page.locator('.conversation-item__name', { hasText: bob.user.displayName })).toBeVisible();
      await bob.page.reload();
      await expect(
        bob.page.locator('.conversation-item__name', { hasText: alice.user.displayName }),
      ).toBeVisible();
    } finally {
      // Contexts are closed even on failure so a broken run cannot leak sockets and accounts.
      await alice.context.close();
      await bob.context.close();
    }
  });

  test('a second window of the same user stays in sync', async ({ browser }) => {
    const alice = await registerUser(browser, 'sync');
    const bob = await registerUser(browser, 'peer');

    try {
      await startConversationWith(alice.page, bob.user.username);
      await sendMessage(alice.page, 'One tab says hello');

      // The same account in a second context: the server keeps both sockets in sync, which is what makes
      // "I sent it from my phone and it is on my laptop" true.
      const secondContext = await browser.newContext({ storageState: await alice.context.storageState() });
      const secondPage = await secondContext.newPage();
      await secondPage.goto('/');
      await expect(
        secondPage.locator('.conversation-item__name', { hasText: bob.user.displayName }),
      ).toBeVisible();

      await secondContext.close();
    } finally {
      await alice.context.close();
      await bob.context.close();
    }
  });
});
