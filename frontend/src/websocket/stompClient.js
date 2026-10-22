import { Client, ActivationState } from '@stomp/stompjs';

/**
 * The realtime connection.
 *
 * Three things make this more than a thin wrapper around `@stomp/stompjs`:
 *
 *  1. **Explicit user-visible states.** The UI must be able to say "reconnecting…" rather than
 *     silently dropping messages, so the facade publishes CONNECTED / RECONNECTING / DISCONNECTED /
 *     FAILED and never hides a broken socket.
 *  2. **Reconnect policy from the protocol doc.** Exponential backoff of `min(30s, 1s·2ⁿ)` with ±20 %
 *     jitter to avoid thundering herds; a bounded number of attempts; and *no* retry on 401/403 —
 *     retrying an authentication failure just burns the lockout budget.
 *  3. **Resync on reconnect.** Messages published while the socket was down are lost to the client, so
 *     after every successful reconnect the client asks the REST history endpoint for everything with a
 *     sequence greater than the highest it has seen. The server is authoritative; the socket is only a
 *     fast path.
 */
export const ConnectionState = Object.freeze({
  IDLE: 'IDLE',
  CONNECTING: 'CONNECTING',
  CONNECTED: 'CONNECTED',
  RECONNECTING: 'RECONNECTING',
  DISCONNECTED: 'DISCONNECTED',
  FAILED: 'FAILED',
});

const MAX_ATTEMPTS = 12;
const BASE_DELAY_MS = 1000;
const MAX_DELAY_MS = 30000;
const JITTER = 0.2;

const DEFAULT_SUBSCRIPTIONS = [
  { destination: '/user/queue/messages', handler: 'message' },
  { destination: '/user/queue/events', handler: 'event' },
  { destination: '/user/queue/errors', handler: 'error' },
];

function backoffDelay(attempt) {
  const raw = Math.min(MAX_DELAY_MS, BASE_DELAY_MS * 2 ** attempt);
  const jitter = raw * JITTER * (Math.random() * 2 - 1);
  return Math.round(raw + jitter);
}

export class RealtimeClient {
  /**
   * @param {object} options
   * @param {string} [options.url] absolute WebSocket URL; defaults to same-origin `/ws`
   * @param {(state: string, detail?: object) => void} [options.onStateChange]
   * @param {(payload: object) => void} [options.onMessage]
   * @param {(payload: object) => void} [options.onEvent]
   * @param {(payload: object) => void} [options.onError]
   * @param {(info: {reason: string}) => void} [options.onResync] called after a reconnect so the app can pull missed state
   */
  constructor({
    url,
    onStateChange,
    onMessage,
    onEvent,
    onError,
    onResync,
    onTransportError,
    debug = false,
  } = {}) {
    this.url = url || `${window.location.origin.replace(/^http/, 'ws')}/ws`;
    this.onStateChange = onStateChange || (() => {});
    this.onMessage = onMessage || (() => {});
    this.onEvent = onEvent || (() => {});
    this.onError = onError || (() => {});
    this.onResync = onResync || (() => {});
    this.onTransportError = onTransportError || (() => {});
    this.debug = debug;

    this.state = ConnectionState.IDLE;
    this.attempts = 0;
    this.everConnected = false;
    this.subscriptions = [];
    this.topicSubscriptions = new Map();
    this.client = null;
    this.stopped = false;
    this.reconnectTimer = null;
  }

  get connected() {
    return this.state === ConnectionState.CONNECTED;
  }

  setState(next, detail) {
    if (this.state === next) return;
    this.state = next;
    this.onStateChange(next, detail);
  }

  /** Opens the connection. Idempotent: calling connect() on a live client is a no-op. */
  connect() {
    if (this.client && this.state !== ConnectionState.FAILED && this.state !== ConnectionState.DISCONNECTED) {
      return;
    }
    this.stopped = false;

    const client = new Client({
      brokerURL: this.url,
      // The session lives in an HttpOnly cookie, which the browser attaches to the handshake
      // automatically; there is no token to put in connectHeaders and nothing for XSS to steal.
      connectHeaders: {},
      reconnectDelay: 0, // handled explicitly below so backoff and attempt caps are ours
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      // Frame-level logging is opt-in and off by default; when it is on, it goes to the console
      // because that is exactly where a developer debugging a handshake looks.
      debug: this.debug ? (message) => console.warn('[ws]', message) : () => {},
      onConnect: () => this.handleConnect(),
      onStompError: (frame) => {
        // A STOMP ERROR frame is a protocol-level rejection, not a transient failure.
        const message = frame.headers?.message || 'Realtime connection rejected';
        this.setState(ConnectionState.FAILED, { reason: 'STOMP_ERROR', message });
        this.onError({ code: 'WS_REJECTED', message });
      },
      onWebSocketClose: (event) => this.handleClose(event),
      onWebSocketError: () => this.onTransportError(),
    });

    this.client = client;
    this.setState(this.everConnected ? ConnectionState.RECONNECTING : ConnectionState.CONNECTING);
    client.activate();
  }

  handleConnect() {
    this.attempts = 0;
    const reconnected = this.everConnected;
    this.everConnected = true;
    this.setState(ConnectionState.CONNECTED, { reconnected });
    this.subscribeDefaults();

    if (reconnected) {
      // Anything published while we were away is not replayed by the broker; pull it from REST.
      this.onResync({ reason: 'RECONNECTED' });
    }
  }

  handleClose(event) {
    if (this.stopped) {
      this.setState(ConnectionState.DISCONNECTED, { reason: 'CLIENT_STOPPED' });
      return;
    }

    // 401/403 mean the session is gone or the origin was rejected. Retrying cannot fix either, and
    // retrying a login-failure path is exactly what the rate limiter is there to stop.
    if (event?.code === 1008 || event?.code === 4401 || event?.code === 4403) {
      this.setState(ConnectionState.FAILED, { reason: 'UNAUTHORIZED', code: event?.code });
      this.onError({
        code: 'WS_UNAUTHORIZED',
        message: 'Your session ended. Sign in again to keep chatting.',
      });
      this.client = null;
      return;
    }

    this.attempts += 1;
    if (this.attempts > MAX_ATTEMPTS) {
      this.setState(ConnectionState.FAILED, { reason: 'ATTEMPTS_EXHAUSTED', attempts: this.attempts });
      this.onError({
        code: 'WS_UNAVAILABLE',
        message: 'Lost connection to the server. Reload the page to try again.',
      });
      return;
    }

    const delay = backoffDelay(this.attempts - 1);
    this.setState(ConnectionState.RECONNECTING, { attempt: this.attempts, delay });
    this.reconnectTimer = window.setTimeout(() => {
      this.reconnectTimer = null;
      if (!this.stopped) this.connect();
    }, delay);
  }

  subscribeDefaults() {
    DEFAULT_SUBSCRIPTIONS.forEach(({ destination, handler }) => {
      this.subscribe(destination, (frame) => this.dispatch(handler, frame));
    });
  }

  dispatch(handler, frame) {
    let payload = null;
    try {
      payload = frame.body ? JSON.parse(frame.body) : null;
    } catch {
      // A frame we cannot parse is a server bug; surface it instead of throwing into the socket loop.
      this.onError({ code: 'WS_MALFORMED_FRAME', message: 'Received a malformed realtime frame.' });
      return;
    }
    const dispatch = { message: this.onMessage, event: this.onEvent, error: this.onError }[handler];
    dispatch(payload);
  }

  /** Subscribes to a destination, replacing any previous subscription to the same one. */
  subscribe(destination, callback) {
    const existing = this.subscriptions.find((entry) => entry.destination === destination);
    if (existing) existing.callback = callback;
    else this.subscriptions.push({ destination, callback });

    if (this.connected && this.client) {
      const subscription = this.client.subscribe(destination, (frame) => {
        const entry = this.subscriptions.find((item) => item.destination === destination);
        if (entry) entry.callback(frame);
      });
      this.topicSubscriptions.set(destination, subscription);
    }
  }

  /** Subscribes to a conversation topic; the server verifies participation before accepting. */
  subscribeToConversation(conversationId, callback) {
    this.subscribe(`/topic/conversation/${conversationId}`, callback);
  }

  unsubscribeFromConversation(conversationId) {
    this.unsubscribe(`/topic/conversation/${conversationId}`);
  }

  unsubscribe(destination) {
    const subscription = this.topicSubscriptions.get(destination);
    if (subscription?.unsubscribe) subscription.unsubscribe();
    this.topicSubscriptions.delete(destination);
    this.subscriptions = this.subscriptions.filter((entry) => entry.destination !== destination);
  }

  /**
   * Publishes to an application destination.
   *
   * Returns `false` when the socket is not usable so the caller can fall back to REST instead of
   * pretending the action succeeded — the optimistic UI must be able to mark the message FAILED.
   */
  publish(destination, body) {
    if (!this.connected || !this.client) return false;
    try {
      this.client.publish({ destination, body: JSON.stringify(body) });
      return true;
    } catch (error) {
      this.onTransportError(error);
      return false;
    }
  }

  /** Tears the connection down on purpose (logout). Never reconnects afterwards. */
  disconnect() {
    this.stopped = true;
    if (this.reconnectTimer) {
      window.clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.topicSubscriptions.clear();
    this.subscriptions = [];
    if (this.client) {
      const client = this.client;
      this.client = null;
      this.setState(ConnectionState.DISCONNECTED, { reason: 'SIGNED_OUT' });
      client.deactivate().catch(() => {});
    } else {
      this.setState(ConnectionState.DISCONNECTED, { reason: 'SIGNED_OUT' });
    }
  }
}

export { ActivationState };

let singleton = null;

/** Shared client used by the app; one socket per tab is the point of a session-scoped connection. */
export function realtimeClient(options) {
  if (!singleton) singleton = new RealtimeClient(options);
  return singleton;
}

export function resetRealtimeClient() {
  if (singleton) singleton.disconnect();
  singleton = null;
}
