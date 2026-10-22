import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError, request } from './client.js';

/**
 * The HTTP layer is where three product rules live, so it is tested directly rather than through a
 * component: no credentials in JavaScript, CSRF on every unsafe verb, and one structured error type for
 * every failure mode.
 */
describe('request', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=csrf-value';
  });

  afterEach(() => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT';
  });

  it('sends the CSRF header on unsafe methods and never a token', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('{}', { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    await request('/api/v1/conversations', { method: 'POST', body: { peerUserId: 'x' } });

    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers['X-XSRF-TOKEN']).toBe('csrf-value');
    expect(init.credentials).toBe('include');
    expect(JSON.stringify(init.headers)).not.toMatch(/bearer|authorization/i);
  });

  it('does not send a CSRF header on safe methods', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('{"items":[]}', { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);

    await request('/api/v1/conversations');

    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers['X-XSRF-TOKEN']).toBeUndefined();
  });

  it('turns the server error contract into an ApiError', async () => {
    const body = JSON.stringify({
      timestamp: '2026-01-01T00:00:00Z',
      status: 429,
      code: 'RATE_LIMITED',
      message: 'Too many requests',
      path: '/api/v1/messages',
      requestId: 'req-1',
      details: [],
    });
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(body, { status: 429 })));

    await expect(request('/api/v1/messages')).rejects.toMatchObject({
      name: 'ApiError',
      status: 429,
      code: 'RATE_LIMITED',
      message: 'Too many requests',
      requestId: 'req-1',
    });
  });

  it('reports a transport failure as a retryable network error, not a crash', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    const error = await request('/api/v1/conversations').catch((thrown) => thrown);
    expect(error).toBeInstanceOf(ApiError);
    expect(error.isNetworkError).toBe(true);
    expect(error.isRetryable).toBe(true);
  });

  it('gives up on a server that never answers instead of hanging forever', async () => {
    // A fetch that only settles when aborted — the shape of a backend that is not listening, or a
    // proxy holding the socket open. Without the deadline this promise never rejects.
    vi.stubGlobal(
      'fetch',
      vi.fn(
        (_url, init) =>
          new Promise((_resolve, reject) => {
            init.signal?.addEventListener('abort', () => reject(init.signal.reason));
          }),
      ),
    );

    const error = await request('/api/v1/auth/me', { timeout: 30 }).catch((thrown) => thrown);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.code).toBe('TIMEOUT');
    expect(error.isRetryable).toBe(true);
  });

  it('does not mistake a caller cancellation for a timeout', async () => {
    const controller = new AbortController();
    vi.stubGlobal(
      'fetch',
      vi.fn(
        (_url, init) =>
          new Promise((_resolve, reject) => {
            init.signal?.addEventListener('abort', () => reject(init.signal.reason));
          }),
      ),
    );

    const pending = request('/api/v1/conversations', { signal: controller.signal, timeout: 5000 });
    controller.abort();

    const error = await pending.catch((thrown) => thrown);
    expect(error.name).toBe('AbortError');
    expect(error).not.toBeInstanceOf(ApiError);
  });

  it('explains a gateway error instead of saying "Something went wrong"', async () => {
    // Exactly what Vite's dev proxy returns when the backend is not running: a status, no JSON body.
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('', { status: 502 })));

    const error = await request('/api/v1/auth/me').catch((thrown) => thrown);

    expect(error.code).toBe('BAD_GATEWAY');
    expect(error.message).toMatch(/not running|still starting/i);
    expect(error.isRetryable).toBe(true);
  });

  it('returns null for 204 responses instead of throwing on empty bodies', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })));
    await expect(request('/api/v1/auth/logout', { method: 'POST' })).resolves.toBeNull();
  });
});
