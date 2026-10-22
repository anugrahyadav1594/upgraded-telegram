import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../api/client.js';
import { SessionStatus, useSessionStore } from './session.js';

vi.mock('../api/auth.js', () => ({
  authApi: { me: vi.fn() },
}));

const { authApi } = await import('../api/auth.js');

/**
 * The start-up session check decides what the user sees first, so its outcomes are pinned here.
 *
 * The distinction that matters: 401 is an *answer* (no session — show the login form), while a
 * transport failure is the *absence* of an answer (show "cannot reach the server" and offer a retry).
 * Collapsing the two is what leaves a user staring at a spinner, or at a login form that cannot work.
 */
describe('session bootstrap', () => {
  beforeEach(() => {
    useSessionStore.setState({ status: SessionStatus.UNKNOWN, user: null, error: null });
    vi.resetAllMocks();
  });

  it('treats 401 as signed out, with no error to report', async () => {
    authApi.me.mockRejectedValue(new ApiError({ status: 401, code: 'UNAUTHENTICATED' }));

    await useSessionStore.getState().bootstrap();

    const state = useSessionStore.getState();
    expect(state.status).toBe(SessionStatus.ANONYMOUS);
    expect(state.error).toBeNull();
  });

  it('stays undecided when the backend cannot be reached, and keeps the reason', async () => {
    authApi.me.mockRejectedValue(
      new ApiError({ status: 0, code: 'NETWORK_ERROR', message: 'Cannot reach the server.' }),
    );

    await useSessionStore.getState().bootstrap();

    const state = useSessionStore.getState();
    expect(state.status).toBe(SessionStatus.UNKNOWN);
    expect(state.error?.code).toBe('NETWORK_ERROR');
  });

  it('treats a timeout the same way — unknown, not signed out', async () => {
    authApi.me.mockRejectedValue(
      new ApiError({ status: 0, code: 'TIMEOUT', message: 'The server did not respond within 8s.' }),
    );

    await useSessionStore.getState().bootstrap();

    expect(useSessionStore.getState().status).toBe(SessionStatus.UNKNOWN);
  });

  it('retry clears the previous failure before asking again', async () => {
    authApi.me.mockRejectedValueOnce(new ApiError({ status: 0, code: 'NETWORK_ERROR', message: 'down' }));
    await useSessionStore.getState().bootstrap();
    expect(useSessionStore.getState().error).not.toBeNull();

    authApi.me.mockResolvedValueOnce({ id: 'u1', username: 'alex', profileComplete: true });
    await useSessionStore.getState().retry();

    const state = useSessionStore.getState();
    expect(state.status).toBe(SessionStatus.AUTHENTICATED);
    expect(state.user.username).toBe('alex');
    expect(state.error).toBeNull();
  });

  it('signs in and out through the same state', async () => {
    authApi.login = vi.fn().mockResolvedValue({ id: 'u1', username: 'alex' });
    await useSessionStore.getState().login({ identifier: 'alex', password: 'x' });
    expect(useSessionStore.getState().status).toBe(SessionStatus.AUTHENTICATED);

    useSessionStore.getState().markSignedOut();
    expect(useSessionStore.getState().status).toBe(SessionStatus.ANONYMOUS);
    expect(useSessionStore.getState().user).toBeNull();
  });
});
