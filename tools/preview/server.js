/**
 * Preview harness — a stand-in for the backend, for demos and screenshots only.
 *
 * ## What this is, and what it is not
 *
 * It is **not** part of the application. The application's API is `backend/` (Java + PostgreSQL +
 * Redis); nothing in `frontend/` or `backend/` knows this file exists. It lives under `tools/` and
 * exists so the UI can be demonstrated, screenshotted or reviewed on a machine where the real backend
 * cannot run — a machine without a JDK, or one working offline, or a CI job that only needs pictures.
 *
 * It is deliberately minimal: enough of `docs/api-specification.md` to render every screen, and a
 * small STOMP-over-WebSocket server so the realtime layer is genuinely exercised (the client connects,
 * subscribes, sends, and receives frames — it is not bypassed or stubbed inside the app).
 *
 * ## Running it
 *
 *     node tools/preview/server.js            # listens on 8080, where the dev proxy expects the API
 *
 * Then start the frontend normally: `cd frontend && npm run dev` and open http://localhost:5173.
 *
 * ## Honesty rules this harness follows
 *
 * * It never invents data the UI cannot get from the real API: every response matches the documented
 *   shape, so the frontend's real parsing, error handling and state machines are what you see.
 * * It is *not* wired into `npm run dev`, the Docker build, or the test suite. If it were, the app
 *   would be pretending. Screenshots generated with it are labelled as such.
 * * Writes are held in memory and lost on restart, which is what stops anyone mistaking it for a
 *   backend.
 */

import { createServer } from 'node:http';
import { randomUUID } from 'node:crypto';
import { WebSocketServer } from 'ws';

import {
  blocks,
  conversations,
  me,
  messages,
  notificationSettings,
  notifications,
  privacy,
  publicProfile,
  sessions,
} from './fixtures.js';

const PORT = Number(process.env.PREVIEW_PORT || 8080);
const COOKIE_NAME = 'preview_session';

// ---------------------------------------------------------------------------------------
// In-memory state. Mutations from the UI are applied here so the interface behaves normally
// (marking a conversation read actually clears the badge, sending appends, and so on).
// ---------------------------------------------------------------------------------------
const state = {
  conversations: structuredClone(conversations),
  messages: { [conversations[0].id]: structuredClone(messages) },
  notifications: structuredClone(notifications),
  notificationSettings: { ...notificationSettings },
  privacy: { ...privacy },
  blocks: structuredClone(blocks),
  sessions: structuredClone(sessions),
  account: { ...me },
};

const sockets = new Set();

function messagesFor(conversationId) {
  if (!state.messages[conversationId]) state.messages[conversationId] = [];
  return state.messages[conversationId];
}

function page(items, limit) {
  const size = Number(limit) || 30;
  return {
    items: items.slice(0, size),
    nextCursor: items.length > size ? `cursor-${size}` : null,
    hasMore: items.length > size,
    limit: size,
  };
}

function json(res, status, body, extraHeaders = {}) {
  const payload = body === undefined ? '' : JSON.stringify(body);
  res.writeHead(status, {
    'Content-Type': 'application/json',
    // The SPA reads this cookie to echo it back in X-XSRF-TOKEN, exactly as it does against the real
    // backend. Without it every mutation from the UI would fail, and the harness would be useless for
    // screenshotting anything that writes.
    'Set-Cookie': `XSRF-TOKEN=preview-token; Path=/; SameSite=Lax`,
    ...extraHeaders,
  });
  res.end(payload);
}

function readBody(req) {
  return new Promise((resolve) => {
    let raw = '';
    req.on('data', (chunk) => {
      raw += chunk;
    });
    req.on('end', () => {
      if (!raw) return resolve({});
      try {
        resolve(JSON.parse(raw));
      } catch {
        resolve({});
      }
    });
  });
}

function isAuthenticated(req) {
  return (req.headers.cookie || '').includes(`${COOKIE_NAME}=1`);
}

// ---------------------------------------------------------------------------------------
// The API surface the frontend actually calls. Anything not listed returns 404 with the same
// structured error body the backend uses, so a missing endpoint is loud rather than silent.
// ---------------------------------------------------------------------------------------
async function handleApi(req, res, url) {
  const path = url.pathname;
  const method = req.method;
  const body = ['POST', 'PATCH', 'PUT'].includes(method) ? await readBody(req) : {};

  const authed = isAuthenticated(req);
  const publicPaths = [
    '/api/v1/auth/login',
    '/api/v1/auth/register',
    '/api/v1/auth/forgot-password',
    '/api/v1/auth/reset-password',
  ];
  if (!authed && !publicPaths.includes(path)) {
    return json(res, 401, {
      status: 401,
      code: 'UNAUTHENTICATED',
      message: 'Authentication required',
      path,
    });
  }

  // --- authentication -----------------------------------------------------------------
  if (path === '/api/v1/auth/me' && method === 'GET') {
    return json(res, 200, state.account);
  }
  if ((path === '/api/v1/auth/login' || path === '/api/v1/auth/register') && method === 'POST') {
    return json(
      res,
      path.endsWith('register') ? 201 : 200,
      { user: state.account, session: { expiresAt: new Date(Date.now() + 30 * 864e5).toISOString() } },
      { 'Set-Cookie': [`${COOKIE_NAME}=1; Path=/; SameSite=Lax`, 'XSRF-TOKEN=preview-token; Path=/; SameSite=Lax'] },
    );
  }
  if (path === '/api/v1/auth/logout' || path === '/api/v1/auth/logout-all') {
    return json(res, 204, undefined, {
      'Set-Cookie': `${COOKIE_NAME}=; Path=/; Max-Age=0; SameSite=Lax`,
    });
  }
  if (path === '/api/v1/auth/sessions' && method === 'GET') {
    return json(res, 200, state.sessions);
  }
  if (path.startsWith('/api/v1/auth/sessions/') && method === 'DELETE') {
    return json(res, 204);
  }
  if (path === '/api/v1/auth/forgot-password') {
    return json(res, 202, { message: 'If that account exists, a reset link is on its way.' });
  }
  if (path === '/api/v1/auth/change-password' || path === '/api/v1/auth/reset-password') {
    return json(res, 200, { message: 'Password updated.' });
  }

  // --- account, profile, privacy, blocks ----------------------------------------------
  if (path === '/api/v1/users/me' && method === 'GET') return json(res, 200, state.account);
  if (path === '/api/v1/users/me' && method === 'PATCH') {
    state.account = { ...state.account, ...body };
    return json(res, 200, state.account);
  }
  if (path === '/api/v1/users/me' && method === 'DELETE') return json(res, 204);
  if (path === '/api/v1/privacy' && method === 'GET') return json(res, 200, state.privacy);
  if (path === '/api/v1/privacy' && method === 'PATCH') {
    state.privacy = { ...state.privacy, ...body };
    return json(res, 200, state.privacy);
  }
  if (path === '/api/v1/blocks' && method === 'GET') return json(res, 200, state.blocks);
  if (path.endsWith('/block') && method === 'POST') {
    const userId = path.split('/')[4];
    state.blocks = [
      ...state.blocks,
      { userId, username: 'blocked-user', displayName: 'Blocked User', avatarUrl: null, blockedAt: new Date().toISOString() },
    ];
    return json(res, 204);
  }
  if (path.endsWith('/block') && method === 'DELETE') {
    const userId = path.split('/')[4];
    state.blocks = state.blocks.filter((b) => b.userId !== userId);
    return json(res, 204);
  }
  if (path === '/api/v1/users/search' && method === 'GET') {
    const term = (url.searchParams.get('q') || '').toLowerCase();
    const results = ['ghost', 'developer', 'testuser']
      .map((u) => publicProfile(u))
      .filter((p) => p && (!term || p.username.includes(term) || p.displayName.toLowerCase().includes(term)));
    return json(res, 200, { items: results, nextCursor: null, hasMore: false, limit: 20 });
  }
  const profileMatch = path.match(/^\/api\/v1\/users\/([^/]+)\/presence$/);
  if (profileMatch && method === 'GET') {
    const profile = publicProfile(profileMatch[1]);
    if (!profile) return json(res, 404, { status: 404, code: 'NOT_FOUND', message: 'User not found', path });
    return json(res, 200, {
      userId: profile.id,
      status: profile.online ? 'ONLINE' : 'OFFLINE',
      lastSeenAt: profile.lastSeenAt,
      visible: true,
    });
  }
  const publicProfileMatch = path.match(/^\/api\/v1\/users\/([^/]+)$/);
  if (publicProfileMatch && method === 'GET') {
    const profile = publicProfile(publicProfileMatch[1]);
    if (!profile) return json(res, 404, { status: 404, code: 'NOT_FOUND', message: 'Profile not found', path });
    return json(res, 200, profile);
  }

  // --- conversations -------------------------------------------------------------------
  if (path === '/api/v1/conversations' && method === 'GET') {
    return json(res, 200, page(state.conversations, url.searchParams.get('limit')));
  }
  if (path === '/api/v1/conversations' && method === 'POST') {
    // The UI addresses the peer by id and never sends a conversation id — same rule as the real API.
    const existing = state.conversations[0];
    return json(res, 200, { conversation: existing, created: false });
  }
  const conversationMatch = path.match(/^\/api\/v1\/conversations\/([0-9a-f-]{36})(\/.*)?$/);
  if (conversationMatch) {
    const conversation = state.conversations.find((c) => c.id === conversationMatch[1]);
    const suffix = conversationMatch[2] || '';
    if (!conversation) {
      return json(res, 404, { status: 404, code: 'NOT_FOUND', message: 'Conversation not found', path });
    }
    if (suffix === '' && method === 'GET') return json(res, 200, conversation);
    if (suffix === '' && method === 'PATCH') {
      Object.assign(conversation, body);
      return json(res, 200, conversation);
    }
    if (suffix === '' && method === 'DELETE') return json(res, 204);
    if (suffix === '/read' && method === 'POST') {
      conversation.unreadCount = 0;
      return json(res, 204);
    }
    if (suffix === '/typing' && method === 'POST') return json(res, 204);
    if (suffix === '/messages' && method === 'GET') {
      const afterSequence = Number(url.searchParams.get('afterSequence'));
      const all = messagesFor(conversation.id);
      if (afterSequence) {
        return json(res, 200, page(all.filter((m) => (m.sequence || 0) > afterSequence), 100));
      }
      return json(res, 200, page([...all].reverse(), url.searchParams.get('limit')));
    }
    if (suffix === '/messages/resync' && method === 'GET') {
      const afterSequence = Number(url.searchParams.get('afterSequence')) || 0;
      return json(res, 200, page(messagesFor(conversation.id).filter((m) => (m.sequence || 0) > afterSequence), 100));
    }
    if (suffix === '/messages' && method === 'POST') {
      const stored = appendMessage(conversation.id, {
        content: body.content,
        clientMessageId: body.clientMessageId,
        messageType: body.messageType || 'TEXT',
        senderId: state.account.id,
      });
      return json(res, 201, stored, { Location: `/api/v1/messages/${stored.id}` });
    }
  }

  // --- messages ------------------------------------------------------------------------
  const messageMatch = path.match(/^\/api\/v1\/messages\/([0-9a-f-]{36})(\/.*)?$/);
  if (messageMatch) {
    const [, messageId, suffix = ''] = messageMatch;
    const found = Object.values(state.messages)
      .flat()
      .find((m) => m.id === messageId);
    if (suffix === '' && method === 'PATCH' && found) {
      found.content = body.content;
      found.editedAt = new Date().toISOString();
      return json(res, 200, found);
    }
    if (suffix === '' && method === 'DELETE' && found) {
      found.content = null;
      found.deletedAt = new Date().toISOString();
      return json(res, 200, found);
    }
    if (suffix === '/read') return json(res, 204);
    if (suffix === '/reaction' && found) {
      const emoji = body.emoji || url.searchParams.get('emoji');
      found.reactions = found.reactions || [];
      const existing = found.reactions.find((r) => r.emoji === emoji);
      if (existing) {
        existing.count += method === 'DELETE' ? -1 : existing.reactedByMe ? -1 : 1;
        existing.reactedByMe = method === 'DELETE' ? false : !existing.reactedByMe;
        found.reactions = found.reactions.filter((r) => r.count > 0);
      } else if (method === 'POST') {
        found.reactions.push({ emoji, count: 1, reactedByMe: true });
      }
      return json(res, 200, found);
    }
  }

  // --- notifications --------------------------------------------------------------------
  if (path === '/api/v1/notifications' && method === 'GET') {
    const unreadOnly = url.searchParams.get('unreadOnly') === 'true';
    const items = unreadOnly ? state.notifications.filter((n) => !n.read) : state.notifications;
    return json(res, 200, page(items, url.searchParams.get('limit')));
  }
  if (path === '/api/v1/notifications/read' && method === 'POST') {
    const ids = body.ids;
    state.notifications.forEach((n) => {
      if (!ids || ids.length === 0 || ids.includes(n.id)) n.read = true;
    });
    return json(res, 200, { unreadCount: state.notifications.filter((n) => !n.read).length });
  }
  if (path === '/api/v1/notifications' && method === 'DELETE') {
    state.notifications = [];
    return json(res, 204);
  }
  if (path.startsWith('/api/v1/notifications/') && method === 'DELETE') {
    const id = path.split('/').pop();
    state.notifications = state.notifications.filter((n) => n.id !== id);
    return json(res, 204);
  }
  if (path === '/api/v1/notifications/settings' && method === 'GET') {
    return json(res, 200, state.notificationSettings);
  }
  if (path === '/api/v1/notifications/settings' && method === 'PATCH') {
    state.notificationSettings = { ...state.notificationSettings, ...body };
    return json(res, 200, state.notificationSettings);
  }

  // --- moderation ------------------------------------------------------------------------
  if (path === '/api/v1/reports' && method === 'POST') {
    return json(res, 201, {
      id: randomUUID(),
      targetType: body.targetType,
      reason: body.reason,
      status: 'OPEN',
      createdAt: new Date().toISOString(),
    });
  }
  if (path === '/api/v1/reports/mine' && method === 'GET') return json(res, 200, []);

  if (path === '/actuator/health') return json(res, 200, { status: 'UP' });

  return json(res, 404, {
    status: 404,
    code: 'NOT_FOUND',
    message: `No preview handler for ${method} ${path}`,
    path,
  });
}

// ---------------------------------------------------------------------------------------
// Message store, shared by REST and STOMP so a message sent over the socket is visible to a
// subsequent history fetch (which is what the UI does after a message.new frame).
// ---------------------------------------------------------------------------------------
let sequenceCounter = 1000;

function appendMessage(conversationId, { content, senderId, clientMessageId = null, messageType = 'TEXT' }) {
  const store = messagesFor(conversationId);
  const message = {
    id: randomUUID(),
    conversationId,
    senderId,
    content,
    messageType,
    clientMessageId,
    sequence: ++sequenceCounter,
    replyTo: null,
    reactions: [],
    receipt: senderId === state.account.id ? { status: 'SENT', deliveredAt: null, readAt: null } : null,
    editedAt: null,
    deletedAt: null,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  };
  store.push(message);

  const conversation = state.conversations.find((c) => c.id === conversationId);
  if (conversation) {
    conversation.lastMessage = {
      id: message.id,
      senderId,
      preview: content.length > 80 ? `${content.slice(0, 77)}…` : content,
      messageType,
      createdAt: message.createdAt,
      deleted: false,
    };
    conversation.lastActivityAt = message.createdAt;
  }
  return message;
}

// ---------------------------------------------------------------------------------------
// STOMP over WebSocket.
//
// Enough of the protocol for @stomp/stompjs to connect, subscribe and exchange frames: CONNECT →
// CONNECTED, SUBSCRIBE/UNSUBSCRIBE bookkeeping, and SEND handling for the application destinations.
// Heartbeats are negotiated off (0,0) so a screenshot run never depends on timing.
// ---------------------------------------------------------------------------------------
function encodeFrame(command, headers, body = '') {
  const headerLines = Object.entries(headers)
    .map(([key, value]) => `${key}:${value}`)
    .join('\n');
  return `${command}\n${headerLines}\n\n${body}\0`;
}

function envelope(type, payload) {
  return JSON.stringify({
    eventId: randomUUID(),
    type,
    occurredAt: new Date().toISOString(),
    payload,
  });
}

function sendTo(socket, destination, headers, frame) {
  for (const subscription of socket.subscriptions.values()) {
    if (subscription.destination !== destination) continue;
    socket.send(
      encodeFrame('MESSAGE', {
        subscription: subscription.id,
        destination,
        'message-id': randomUUID(),
        'content-type': 'application/json',
        ...headers,
      }, frame),
    );
  }
}

function broadcast(destination, frame) {
  for (const socket of sockets) sendTo(socket, destination, {}, frame);
}

function handleStomp(socket, raw) {
  const command = raw.split('\n')[0].trim();
  const headerBlock = raw.split('\n\n')[0].split('\n').slice(1);
  const headers = Object.fromEntries(
    headerBlock.filter((line) => line.includes(':')).map((line) => {
      const index = line.indexOf(':');
      return [line.slice(0, index), line.slice(index + 1)];
    }),
  );
  const body = raw.split('\n\n').slice(1).join('\n\n').replace(/\0$/, '');

  if (command === 'CONNECT' || command === 'STOMP') {
    socket.send(encodeFrame('CONNECTED', { version: '1.2', 'heart-beat': '0,0', session: randomUUID() }));
    return;
  }
  if (command === 'SUBSCRIBE') {
    socket.subscriptions.set(headers.id, {
      id: headers.id,
      destination: headers.destination,
      userId: headers.destination.includes('/user/') ? state.account.id : null,
    });
    return;
  }
  if (command === 'UNSUBSCRIBE') {
    socket.subscriptions.delete(headers.id);
    return;
  }
  if (command !== 'SEND') return;

  let payload = {};
  try {
    payload = body ? JSON.parse(body) : {};
  } catch {
    return; // a malformed command is dropped, exactly as the real controller would reject it
  }

  const { destination } = headers;
  if (destination === '/app/chat.send') {
    const stored = appendMessage(payload.conversationId, {
      content: payload.content,
      senderId: state.account.id,
      clientMessageId: payload.clientMessageId,
      messageType: payload.messageType,
    });

    // The sender's own acknowledgement, then the recipient-side broadcast — the same two frames the
    // backend produces, which is what drives the SENDING → SENT transition in the UI.
    broadcast('/user/queue/messages', envelope('message.ack', { clientMessageId: stored.clientMessageId, message: stored }));
    broadcast(`/topic/conversation/${stored.conversationId}`, envelope('message.new', { message: stored }));
    broadcast('/user/queue/messages', envelope('message.new', { message: stored }));
    return;
  }
  if (destination === '/app/chat.typing') {
    const frame = envelope(payload.isTyping ? 'typing.started' : 'typing.stopped', {
      conversationId: payload.conversationId,
      userId: state.account.id,
      displayName: state.account.displayName,
      expiresInMs: 5000,
    });
    // Both routes, as documented: the conversation topic and the user queue. The app subscribes to the
    // user queues; publishing only to the topic would leave the indicator invisible.
    broadcast(`/topic/conversation/${payload.conversationId}`, frame);
    broadcast('/user/queue/events', frame);
    return;
  }
  if (destination === '/app/chat.read' || destination === '/app/chat.delivered') {
    const conversation = state.conversations.find((c) => c.id === payload.conversationId);
    if (conversation) conversation.unreadCount = 0;
    return;
  }
  // /app/message.edit, /app/message.delete, /app/message.reaction: the REST equivalents above already
  // apply them, and the UI refetches on the resulting frame. Ignoring them keeps this file small.
}

// ---------------------------------------------------------------------------------------
// Control endpoints used by the screenshot script (`tools/preview/capture.mjs`) to stage a scene:
// it can push a frame into the live socket or reset state between shots. Namespaced under
// /__preview so it can never collide with a real API route.
// ---------------------------------------------------------------------------------------
async function handleControl(req, res, url) {
  const body = req.method === 'POST' ? await readBody(req) : {};

  if (url.pathname === '/__preview/emit' && req.method === 'POST') {
    const { type, payload = {}, destination } = body;

    // A message.new also has to exist in the store, because the client refetches history after it.
    if (type === 'message.new' && payload.conversationId) {
      const stored = appendMessage(payload.conversationId, {
        content: payload.content || 'Hello from the preview harness',
        senderId: payload.senderId || conversations[0].peer.id,
      });
      broadcast(destination || `/topic/conversation/${payload.conversationId}`, envelope('message.new', { message: stored }));
      broadcast('/user/queue/messages', envelope('message.new', { message: stored }));
      return json(res, 200, { emitted: 'message.new', messageId: stored.id });
    }

    const frame = envelope(type, payload);
    if (destination) broadcast(destination, frame);
    else {
      broadcast('/user/queue/events', frame);
      broadcast('/user/queue/messages', frame);
    }
    return json(res, 200, { emitted: type });
  }

  if (url.pathname === '/__preview/reset' && req.method === 'POST') {
    // `empty: true` clears the conversation list so the first-run empty state can be captured — that
    // screen is as much a part of the interface as the busy one, and it is the one a new user sees.
    state.conversations = body.empty ? [] : structuredClone(conversations);
    state.messages = body.empty ? {} : { [conversations[0].id]: structuredClone(messages) };
    state.notifications = structuredClone(notifications);
    state.notificationSettings = { ...notificationSettings };
    state.account = { ...me };
    sequenceCounter = 1000;
    return json(res, 200, { reset: true, empty: Boolean(body.empty) });
  }

  return json(res, 404, { status: 404, code: 'NOT_FOUND', message: 'Unknown control endpoint' });
}

// ---------------------------------------------------------------------------------------
// Wiring
// ---------------------------------------------------------------------------------------
const server = createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  try {
    if (url.pathname.startsWith('/__preview/')) return await handleControl(req, res, url);
    if (url.pathname.startsWith('/api/') || url.pathname === '/actuator/health') {
      return await handleApi(req, res, url);
    }
    res.writeHead(404, { 'Content-Type': 'text/plain' });
    res.end('The preview harness serves /api/v1/** and /ws only. The SPA is served by Vite.\n');
  } catch (error) {
    console.error('preview harness error:', error);
    json(res, 500, { status: 500, code: 'PREVIEW_ERROR', message: String(error?.message || error) });
  }
});

const wss = new WebSocketServer({ noServer: true });

server.on('upgrade', (req, socket, head) => {
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  if (url.pathname !== '/ws') {
    socket.destroy();
    return;
  }
  wss.handleUpgrade(req, socket, head, (ws) => {
    ws.subscriptions = new Map();
    sockets.add(ws);
    ws.on('message', (data) => {
      for (const frame of String(data).split('\0')) {
        if (frame.trim()) handleStomp(ws, frame.trim());
      }
    });
    ws.on('close', () => sockets.delete(ws));
    ws.on('error', () => sockets.delete(ws));
  });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`Preview harness listening on http://0.0.0.0:${PORT}`);
  console.log(`  API:  http://localhost:${PORT}/api/v1/**`);
  console.log(`  WS:   ws://localhost:${PORT}/ws`);
  console.log('  Sign in with any credentials — this is a stub, not a backend.');
});
