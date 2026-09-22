import { expect } from '@playwright/test';

/**
 * Shared helpers for the end-to-end suite.
 *
 * Accounts are generated per run so a suite can be re-run against the same database without cleanup, and
 * so a failure can never be caused by a previous run's leftovers. The password meets the server's policy
 * (12+ characters, non-trivial); it is a test fixture, not a credential.
 */
export const TEST_PASSWORD = 'e2e-Passphrase-2026!';

export function uniqueHandle(prefix) {
  const suffix = Math.random().toString(36).slice(2, 8);
  const stamp = Date.now().toString(36).slice(-4);
  return `${prefix}${suffix}${stamp}`.toLowerCase();
}

/** Registers a user in a fresh browser context and returns the context plus the identity. */
export async function registerUser(browser, prefix) {
  const context = await browser.newContext();
  const page = await context.newPage();
  const username = uniqueHandle(prefix);
  const user = { username, displayName: `${prefix} ${username.slice(-4)}`, email: `${username}@example.test` };

  await page.goto('/register');
  await page.getByLabel('Email address').fill(user.email);
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Display name').fill(user.displayName);
  await page.getByLabel('Password').fill(TEST_PASSWORD);
  await page.getByRole('button', { name: /create account/i }).click();

  // A fresh account lands on onboarding; complete it so the account is usable as a peer.
  await expect(page.getByRole('heading', { name: /set up your profile/i })).toBeVisible();
  await page.getByLabel('Display name').fill(user.displayName);
  await page.getByRole('button', { name: /start chatting/i }).click();
  await expect(page.getByRole('heading', { name: 'Chats' })).toBeVisible();

  return { context, page, user };
}

/** Starts a conversation with `username` from the conversation list pane. */
export async function startConversationWith(page, username) {
  await page.getByRole('button', { name: /start a new conversation/i }).click();
  const dialog = page.getByRole('dialog', { name: /new conversation/i });
  await dialog.getByLabel(/find someone by username/i).fill(username);

  const result = dialog.getByRole('button', { name: new RegExp(`@${username}`, 'i') });
  await expect(result).toBeVisible();
  await result.click();
  await expect(dialog).toBeHidden();
}

/** Sends a message in the open thread and waits for it to leave the pending state. */
export async function sendMessage(page, text) {
  await page.getByLabel('Message').fill(text);
  await page.getByRole('button', { name: /send message/i }).click();
  const bubble = page.locator('.message--out', { hasText: text }).last();
  await expect(bubble).toBeVisible();
  await expect(bubble).not.toHaveClass(/message--pending/);
  return bubble;
}

/** Waits for an incoming message without reloading the page — the realtime assertion. */
export async function expectIncomingMessage(page, text) {
  const bubble = page.locator('.message--in', { hasText: text }).last();
  await expect(bubble).toBeVisible();
  return bubble;
}
