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

/**
 * Requests are bounded in time.
 *
 * Without this, a backend that is not running (or a proxy holding a connection open) leaves the caller
 * waiting forever: the UI shows a spinner with no explanation and no way out except a reload. Fifteen
 * seconds is far longer than any healthy endpoint needs and short enough to be worth waiting for; the
 * session check on start-up uses a tighter budget of its own (see `authApi.me`).
 */
const DEFAULT_TIMEOUT_MS = 15000;

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

/**
 * Human-readable fallbacks for errors that do not come from the API.
 *
 * A gateway in front of the backend (Vite's dev proxy, nginx, a load balancer) answers with a status and
 * an HTML or empty body when the API is down. Reporting those as "Something went wrong" hides the one
 * fact the user needs: the API itself never answered.
 */
const GATEWAY_ERRORS = {
  502: ['BAD_GATEWAY', 'The API did not answer — it may still be starting, or it is not running.'],
  503: ['SERVICE_UNAVAILABLE', 'The server is temporarily unavailable. Try again in a moment.'],
  504: ['GATEWAY_TIMEOUT', 'The server took too long to answer.'],
};

function fallbackCode(status) {
  if (GATEWAY_ERRORS[status]) return GATEWAY_ERRORS[status][0];
  return status === 0 ? 'NETWORK_ERROR' : 'UNKNOWN_ERROR';
}

function fallbackMessage(status) {
  if (GATEWAY_ERRORS[status]) return GATEWAY_ERRORS[status][1];
  if (status >= 500) return 'The server ran into a problem. Try again shortly.';
  return 'Something went wrong';
}

function readCookie(name) {
  if (typeof document === 'undefined' || !document.cookie) return null;
  const match = document.cookie
    .split(';')
    .map((part) => part.trim())
    .find((part) => part.startsWith(`${name}=`));
  return match ? decodeURIComponent(match.slice(name.length + 1)) : null;
}

/** An abort signal that fires after `ms`, or `null` where the platform lacks `AbortSignal.timeout`. */
function timeoutSignal(ms) {
  if (!Number.isFinite(ms) || ms <= 0) return null;
  if (typeof AbortSignal === 'undefined' || typeof AbortSignal.timeout !== 'function') return null;
  return AbortSignal.timeout(ms);
}

/**
 * Merges a caller's cancellation signal with our timeout.
 *
 * Both reasons must remain distinguishable: cancelling (a component unmounted, React Query moved on)
 * is not a failure and is re-thrown untouched, while our own deadline becomes a `TIMEOUT` error the UI
 * can explain.
 */
function mergeSignals(callerSignal, deadline) {
  const signals = [callerSignal, deadline].filter(Boolean);
  if (signals.length === 0) return undefined;
  if (signals.length === 1) return signals[0];
  if (typeof AbortSignal.any === 'function') return AbortSignal.any(signals);
  return callerSignal || deadline;
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
export async function request(
  path,
  { method = 'GET', body, params, signal, headers = {}, raw = false, timeout = DEFAULT_TIMEOUT_MS } = {},
) {
  const upperMethod = method.toUpperCase();
  const finalHeaders = { Accept: 'application/json', ...headers };

  if (body !== undefined && !raw) {
    finalHeaders['Content-Type'] = 'application/json';
  }
  if (!SAFE_METHODS.has(upperMethod)) {
    const token = readCookie(CSRF_COOKIE);
    if (token) finalHeaders[CSRF_HEADER] = token;
  }

  const deadline = timeoutSignal(timeout);
  const effectiveSignal = mergeSignals(signal, deadline);

  let response;
  try {
    response = await fetch(buildUrl(path, params), {
      method: upperMethod,
      credentials: 'include',
      headers: finalHeaders,
      signal: effectiveSignal,
      body: body === undefined ? undefined : raw ? body : JSON.stringify(body),
    });
  } catch (error) {
    // Our deadline, not the caller's: report it as a timeout the UI can act on.
    if (deadline?.aborted || error?.name === 'TimeoutError') {
      throw new ApiError({
        status: 0,
        code: 'TIMEOUT',
        message: `The server did not respond within ${Math.round(timeout / 1000)}s.`,
        cause: error,
      });
    }
    // Caller-initiated cancellation is not a failure.
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
      code: payload?.code || fallbackCode(response.status),
      message: payload?.message || fallbackMessage(response.status),
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
