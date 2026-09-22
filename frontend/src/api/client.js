/**
 * The single HTTP entry point for the app.
 *
 * Two rules live here and nowhere else:
 *  1. The session cookie is HttpOnly/SameSite — JavaScript never sees or stores a token, so there is
 *     nothing to attach to a request except the CSRF header the server itself issued.
 *  2. Every error becomes an `ApiError` carrying the server's structured error contract
 *     (`{status, code, message, path, requestId, details}`), so components never parse raw text and a
 *     network failure is distinguishable from a rejection.
 */
const API_BASE = import.meta.env.VITE_API_BASE || '';
const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

/** Thrown for every non-2xx response and for transport failures. */
export class ApiError extends Error {
  constructor({ status, code, message, details, requestId, path, cause }) {
    super(message || 'Something went wrong');
    this.name = 'ApiError';
    this.status = status ?? 0;
    this.code = code || (status === 0 ? 'NETWORK_ERROR' : 'UNKNOWN_ERROR');
    this.details = details || [];
    this.requestId = requestId || null;
    this.path = path || null;
    if (cause) this.cause = cause;
  }

  get isNetworkError() {
    return this.status === 0;
  }

  get isUnauthenticated() {
    return this.status === 401;
  }

  get isRateLimited() {
    return this.status === 429;
  }

  /** True when retrying the same request could plausibly succeed. */
  get isRetryable() {
    return this.status === 0 || this.status === 429 || this.status >= 500;
  }
}

function readCookie(name) {
  if (typeof document === 'undefined' || !document.cookie) return null;
  const match = document.cookie
    .split(';')
    .map((part) => part.trim())
    .find((part) => part.startsWith(`${name}=`));
  return match ? decodeURIComponent(match.slice(name.length + 1)) : null;
}

function buildUrl(path, params) {
  const url = `${API_BASE}${path}`;
  if (!params) return url;
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === null || value === '') return;
    search.append(key, String(value));
  });
  const query = search.toString();
  return query ? `${url}?${query}` : url;
}

/**
 * Performs a request and normalises both failure modes into `ApiError`.
 *
 * `signal` is forwarded so React Query can cancel in-flight work when a component unmounts or a
 * navigation happens — an aborted request resolves as a cancellation, never as a user-visible error.
 */
export async function request(path, { method = 'GET', body, params, signal, headers = {}, raw = false } = {}) {
  const upperMethod = method.toUpperCase();
  const finalHeaders = { Accept: 'application/json', ...headers };

  if (body !== undefined && !raw) {
    finalHeaders['Content-Type'] = 'application/json';
  }
  if (!SAFE_METHODS.has(upperMethod)) {
    const token = readCookie(CSRF_COOKIE);
    if (token) finalHeaders[CSRF_HEADER] = token;
  }

  let response;
  try {
    response = await fetch(buildUrl(path, params), {
      method: upperMethod,
      credentials: 'include',
      headers: finalHeaders,
      signal,
      body: body === undefined ? undefined : raw ? body : JSON.stringify(body),
    });
  } catch (error) {
    if (error?.name === 'AbortError') throw error;
    throw new ApiError({
      status: 0,
      code: 'NETWORK_ERROR',
      message: 'Cannot reach the server. Check your connection and try again.',
      cause: error,
    });
  }

  if (response.status === 204) return null;

  const text = await response.text();
  let payload = null;
  if (text) {
    try {
      payload = JSON.parse(text);
    } catch {
      payload = null;
    }
  }

  if (!response.ok) {
    throw new ApiError({
      status: response.status,
      code: payload?.code,
      message: payload?.message,
      details: payload?.details,
      requestId: payload?.requestId || response.headers.get('X-Request-Id'),
      path: payload?.path,
    });
  }

  return payload;
}

export const http = {
  get: (path, options) => request(path, { ...options, method: 'GET' }),
  post: (path, body, options) => request(path, { ...options, method: 'POST', body }),
  patch: (path, body, options) => request(path, { ...options, method: 'PATCH', body }),
  put: (path, body, options) => request(path, { ...options, method: 'PUT', body }),
  delete: (path, options) => request(path, { ...options, method: 'DELETE' }),
};
