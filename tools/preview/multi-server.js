/**
 * Multi-user preview harness — two users (alex & ghost) can log in from
 * separate browser sessions and actually chat with each other in real time.
 *
 * Usage:
 *   node tools/preview/multi-server.js
 *   cd frontend && VITE_BACKEND_URL=http://localhost:3080 npm run dev
 *
 * Open http://localhost:5173 in a normal window → sign in as "alex"
 * Open http://localhost:5173 in incognito     → sign in as "ghost"
 * They can now send messages back and forth.
 */

import { createServer } from 'node:http';
import { randomUUID } from 'node:crypto';
import { WebSocketServer } from 'ws';

const PORT = Number(process.env.PREVIEW_PORT || 8080);

// -------------------------------------------------------------------------------------
// Users
// -------------------------------------------------------------------------------------
const now = Date.now();
const minutesAgo = (n) => new Date(now - n * 60_000).toISOString();

const users = {
  alex: {
    id: '11111111-1111-4111-8111-111111111111',
    email: 'alex@example.test',
    username: 'alex',
    displayName: 'Alex Rivera',
    avatarUrl: null,
    bio: 'Backend developer. Mostly here for the group chats.',
    usernamePublic: true,
    emailVerified: true,
    profileComplete: true,
    privacy: { showOnlineStatus: true, showLastSeen: true, showProfileInSearch: true, allowMessagesFrom: 'EVERYONE', readReceipts: true },
    unreadNotifications: 0,
    createdAt: minutesAgo(60 * 24 * 30),
  },
  ghost: {
    id: '22222222-2222-4222-8222-222222222222',
    email: 'ghost@example.test',
    username: 'ghost',
    displayName: 'Ghost',
    avatarUrl: null,
    bio: 'Lurker. Reads everything, replies eventually.',
    usernamePublic: true,
    emailVerified: true,
    profileComplete: true,
    privacy: { showOnlineStatus: true, showLastSeen: true, showProfileInSearch: true, allowMessagesFrom: 'EVERYONE', readReceipts: true },
    unreadNotifications: 0,
    createdAt: minutesAgo(60 * 24 * 20),
  },
};

// The single shared conversation between alex and ghost
const CONVO_ID = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';

// -------------------------------------------------------------------------------------
// Shared state
// -------------------------------------------------------------------------------------
const messageStore = [];
let sequenceCounter = 100;

function getConversation(viewerUsername) {
  const viewer = users[viewerUsername];
  const peerUsername = viewerUsername === 'alex' ? 'ghost' : 'alex';
  const peer = users[peerUsername];
  const lastMsg = messageStore[messageStore.length - 1];

  return {
    id: CONVO_ID,
    type: 'DIRECT',
    peer: {
      id: peer.id,
      username: peer.username,
      displayName: peer.displayName,
      avatarUrl: null,
      online: isUserOnline(peerUsername),
      lastSeenAt: null,
    },
    lastMessage: lastMsg
      ? {
          id: lastMsg.id,
          senderId: lastMsg.senderId,
          preview: lastMsg.content?.length > 80 ? `${lastMsg.content.slice(0, 77)}…` : lastMsg.content,
          messageType: 'TEXT',
          createdAt: lastMsg.createdAt,
          deleted: false,
        }
      : null,
    unreadCount: 0,
    muted: false,
    archived: false,
    lastActivityAt: lastMsg?.createdAt || minutesAgo(1),
    createdAt: minutesAgo(60 * 24 * 3),
  };
}

// -------------------------------------------------------------------------------------
// Session tracking: cookie → username
// -------------------------------------------------------------------------------------
const sessionMap = new Map(); // sessionId → username

function getSessionId(req) {
  const cookies = (req.headers.cookie || '').split(';').map((c) => c.trim());
  for (const c of cookies) {
    if (c.startsWith('preview_session=')) {
      return c.slice('preview_session='.length);
    }
  }
  return null;
}

function getUsername(req) {
  const sid = getSessionId(req);
  return sid ? sessionMap.get(sid) : null;
}

// -------------------------------------------------------------------------------------
// WebSocket tracking per user
// -------------------------------------------------------------------------------------
const socketsByUser = new Map(); // username → Set<ws>

function isUserOnline(username) {
  const sockets = socketsByUser.get(username);
  return sockets ? sockets.size > 0 : false;
}

// -------------------------------------------------------------------------------------
// Helpers
// -------------------------------------------------------------------------------------
function json(res, status, body, extraHeaders = {}) {
  const payload = body === undefined ? '' : JSON.stringify(body);
  res.writeHead(status, {
    'Content-Type': 'application/json',
    'Set-Cookie': 'XSRF-TOKEN=preview-token; Path=/; SameSite=Lax',
    ...extraHeaders,
  });
  res.end(payload);
}

function readBody(req) {
  return new Promise((resolve) => {
    let raw = '';
    req.on('data', (chunk) => { raw += chunk; });
    req.on('end', () => {
      if (!raw) return resolve({});
      try { resolve(JSON.parse(raw)); } catch { resolve({}); }
    });
  });
}

function page(items, limit) {
  const size = Number(limit) || 30;
  return { items: items.slice(0, size), nextCursor: null, hasMore: items.length > size, limit: size };
}

function appendMessage(conversationId, { content, senderId, clientMessageId = null, messageType = 'TEXT' }) {
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
    receipt: { status: 'SENT', deliveredAt: null, readAt: null },
    editedAt: null,
    deletedAt: null,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  };
  messageStore.push(message);
  return message;
}

// -------------------------------------------------------------------------------------
// STOMP helpers
// -------------------------------------------------------------------------------------
function encodeFrame(command, headers, body = '') {
  const headerLines = Object.entries(headers).map(([k, v]) => `${k}:${v}`).join('\n');
  return `${command}\n${headerLines}\n\n${body}\0`;
}

function envelope(type, payload) {
  return JSON.stringify({ eventId: randomUUID(), type, occurredAt: new Date().toISOString(), payload });
}

function sendToSocket(socket, destination, frame) {
  for (const sub of socket.subscriptions.values()) {
    if (sub.destination !== destination) continue;
    socket.send(encodeFrame('MESSAGE', {
      subscription: sub.id,
      destination,
      'message-id': randomUUID(),
      'content-type': 'application/json',
    }, frame));
  }
}

function broadcastToUser(username, destination, frame) {
  const sockets = socketsByUser.get(username);
  if (!sockets) return;
  for (const ws of sockets) sendToSocket(ws, destination, frame);
}

function broadcastToAll(destination, frame) {
  for (const [, sockets] of socketsByUser) {
    for (const ws of sockets) sendToSocket(ws, destination, frame);
  }
}

// -------------------------------------------------------------------------------------
// API handler
// -------------------------------------------------------------------------------------
async function handleApi(req, res, url) {
  const path = url.pathname;
  const method = req.method;
  const body = ['POST', 'PATCH', 'PUT'].includes(method) ? await readBody(req) : {};

  const username = getUsername(req);

  // --- auth ---
  if ((path === '/api/v1/auth/login' || path === '/api/v1/auth/register') && method === 'POST') {
    // Determine which user based on identifier, username, or email
    const rawInput = body.identifier || body.username || body.email || '';
    const input = String(rawInput).toLowerCase().replace('@', '');
    let loginAs = 'alex'; // default
    if (input.includes('ghost')) loginAs = 'ghost';
    else if (input.includes('alex')) loginAs = 'alex';
    else if (users[input]) loginAs = input;

    // Only support alex and ghost
    if (!users[loginAs]) loginAs = 'alex';

    const sessionId = randomUUID();
    sessionMap.set(sessionId, loginAs);

    console.log(`✅ [LOGIN] '${rawInput}' resolved to '${loginAs}' (session ${sessionId.slice(0, 8)}…)`);

    return json(
      res,
      path.endsWith('register') ? 201 : 200,
      { user: users[loginAs], session: { expiresAt: new Date(Date.now() + 30 * 864e5).toISOString() } },
      { 'Set-Cookie': [`preview_session=${sessionId}; Path=/; SameSite=Lax`, 'XSRF-TOKEN=preview-token; Path=/; SameSite=Lax'] },
    );
  }

  if (path === '/api/v1/auth/forgot-password') {
    return json(res, 202, { message: 'If that account exists, a reset link is on its way.' });
  }

  // Everything below needs auth
  if (!username) {
    return json(res, 401, { status: 401, code: 'UNAUTHENTICATED', message: 'Authentication required', path });
  }

  const me = users[username];
  const peerUsername = username === 'alex' ? 'ghost' : 'alex';
  const peer = users[peerUsername];

  if (path === '/api/v1/auth/me' && method === 'GET') return json(res, 200, me);
  if (path === '/api/v1/auth/logout' || path === '/api/v1/auth/logout-all') {
    const sid = getSessionId(req);
    if (sid) sessionMap.delete(sid);
    return json(res, 204, undefined, { 'Set-Cookie': 'preview_session=; Path=/; Max-Age=0; SameSite=Lax' });
  }
  if (path === '/api/v1/auth/sessions' && method === 'GET') {
    return json(res, 200, [{
      id: randomUUID(), current: true, userAgent: req.headers['user-agent'] || '',
      ipAddress: '127.0.0.1', createdAt: minutesAgo(1), lastUsedAt: new Date().toISOString(),
      expiresAt: new Date(Date.now() + 30 * 864e5).toISOString(),
    }]);
  }
  if (path.startsWith('/api/v1/auth/sessions/') && method === 'DELETE') return json(res, 204);
  if (path === '/api/v1/auth/change-password' || path === '/api/v1/auth/reset-password') {
    return json(res, 200, { message: 'Password updated.' });
  }

  // --- account / profile ---
  if (path === '/api/v1/users/me' && method === 'GET') return json(res, 200, me);
  if (path === '/api/v1/users/me' && method === 'PATCH') {
    Object.assign(me, body);
    return json(res, 200, me);
  }
  if (path === '/api/v1/users/me' && method === 'DELETE') return json(res, 204);
  if (path === '/api/v1/privacy' && method === 'GET') return json(res, 200, me.privacy);
  if (path === '/api/v1/privacy' && method === 'PATCH') {
    me.privacy = { ...me.privacy, ...body };
    return json(res, 200, me.privacy);
  }
  if (path === '/api/v1/blocks' && method === 'GET') return json(res, 200, []);
  if (path.endsWith('/block') && (method === 'POST' || method === 'DELETE')) return json(res, 204);

  if (path === '/api/v1/users/search' && method === 'GET') {
    const term = (url.searchParams.get('q') || '').toLowerCase();
    const results = Object.values(users)
      .filter((u) => u.username !== username) // don't show self
      .filter((u) => !term || u.username.includes(term) || u.displayName.toLowerCase().includes(term))
      .map((u) => ({
        id: u.id, username: u.username, usernamePublic: true, displayName: u.displayName,
        avatarUrl: null, bio: u.bio, online: isUserOnline(u.username), lastSeenAt: null,
        memberSince: u.createdAt, existingConversationId: CONVO_ID, blockedByMe: false, canMessage: true,
      }));
    return json(res, 200, { items: results, nextCursor: null, hasMore: false, limit: 20 });
  }

  const presenceMatch = path.match(/^\/api\/v1\/users\/([^/]+)\/presence$/);
  if (presenceMatch && method === 'GET') {
    const target = Object.values(users).find((u) => u.username === presenceMatch[1]);
    if (!target) return json(res, 404, { status: 404, code: 'NOT_FOUND', message: 'User not found', path });
    return json(res, 200, { userId: target.id, status: isUserOnline(target.username) ? 'ONLINE' : 'OFFLINE', lastSeenAt: null, visible: true });
  }

  const profileMatch = path.match(/^\/api\/v1\/users\/([^/]+)$/);
  if (profileMatch && method === 'GET') {
    const target = Object.values(users).find((u) => u.username === profileMatch[1]);
    if (!target) return json(res, 404, { status: 404, code: 'NOT_FOUND', message: 'Profile not found', path });
    return json(res, 200, {
      id: target.id, username: target.username, usernamePublic: true, displayName: target.displayName,
      avatarUrl: null, bio: target.bio, online: isUserOnline(target.username), lastSeenAt: null,
      memberSince: target.createdAt, existingConversationId: CONVO_ID, blockedByMe: false, canMessage: true,
    });
  }

  // --- conversations ---
  if (path === '/api/v1/conversations' && method === 'GET') {
    return json(res, 200, page([getConversation(username)], url.searchParams.get('limit')));
  }
  if (path === '/api/v1/conversations' && method === 'POST') {
    return json(res, 200, { conversation: getConversation(username), created: false });
  }

  const convoMatch = path.match(/^\/api\/v1\/conversations\/([0-9a-f-]{36})(\/.*)?$/);
  if (convoMatch) {
    const convoId = convoMatch[1];
    const suffix = convoMatch[2] || '';
    if (convoId !== CONVO_ID) return json(res, 404, { status: 404, code: 'NOT_FOUND', message: 'Conversation not found', path });

    if (suffix === '' && method === 'GET') return json(res, 200, getConversation(username));
    if (suffix === '' && method === 'PATCH') return json(res, 200, getConversation(username));
    if (suffix === '' && method === 'DELETE') return json(res, 204);
    if (suffix === '/read' && method === 'POST') return json(res, 204);
    if (suffix === '/typing' && method === 'POST') return json(res, 204);

    if (suffix === '/messages' && method === 'GET') {
      const afterSequence = Number(url.searchParams.get('afterSequence'));
      const convoMsgs = messageStore.filter((m) => m.conversationId === CONVO_ID);
      if (afterSequence) {
        return json(res, 200, page(convoMsgs.filter((m) => m.sequence > afterSequence), 100));
      }
      return json(res, 200, page([...convoMsgs].reverse(), url.searchParams.get('limit')));
    }
    if (suffix === '/messages/resync' && method === 'GET') {
      const afterSequence = Number(url.searchParams.get('afterSequence')) || 0;
      return json(res, 200, page(messageStore.filter((m) => m.conversationId === CONVO_ID && m.sequence > afterSequence), 100));
    }
    if (suffix === '/messages' && method === 'POST') {
      const stored = appendMessage(CONVO_ID, {
        content: body.content,
        clientMessageId: body.clientMessageId,
        messageType: body.messageType || 'TEXT',
        senderId: me.id,
      });
      return json(res, 201, stored, { Location: `/api/v1/messages/${stored.id}` });
    }
  }

  // --- messages ---
  const msgMatch = path.match(/^\/api\/v1\/messages\/([0-9a-f-]{36})(\/.*)?$/);
  if (msgMatch) {
    const [, messageId, suffix = ''] = msgMatch;
    const found = messageStore.find((m) => m.id === messageId);
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

  // --- notifications ---
  if (path === '/api/v1/notifications' && method === 'GET') return json(res, 200, page([], 30));
  if (path === '/api/v1/notifications/read' && method === 'POST') return json(res, 200, { unreadCount: 0 });
  if (path === '/api/v1/notifications' && method === 'DELETE') return json(res, 204);
  if (path.startsWith('/api/v1/notifications/') && method === 'DELETE') return json(res, 204);
  if (path === '/api/v1/notifications/settings' && method === 'GET') {
    return json(res, 200, { newMessage: true, mentions: true, conversationUpdates: true, moderationUpdates: true, emailDigest: false });
  }
  if (path === '/api/v1/notifications/settings' && method === 'PATCH') return json(res, 200, body);

  // --- moderation ---
  if (path === '/api/v1/reports' && method === 'POST') {
    return json(res, 201, { id: randomUUID(), targetType: body.targetType, reason: body.reason, status: 'OPEN', createdAt: new Date().toISOString() });
  }
  if (path === '/api/v1/reports/mine' && method === 'GET') return json(res, 200, []);

  if (path === '/actuator/health') return json(res, 200, { status: 'UP' });

  return json(res, 404, { status: 404, code: 'NOT_FOUND', message: `No handler for ${method} ${path}`, path });
}

// -------------------------------------------------------------------------------------
// STOMP handler
// -------------------------------------------------------------------------------------
function handleStomp(ws, raw) {
  const command = raw.split('\n')[0].trim();
  const headerBlock = raw.split('\n\n')[0].split('\n').slice(1);
  const headers = Object.fromEntries(
    headerBlock.filter((l) => l.includes(':')).map((l) => {
      const i = l.indexOf(':');
      return [l.slice(0, i), l.slice(i + 1)];
    }),
  );
  const body = raw.split('\n\n').slice(1).join('\n\n').replace(/\0$/, '');

  if (command === 'CONNECT' || command === 'STOMP') {
    ws.send(encodeFrame('CONNECTED', { version: '1.2', 'heart-beat': '0,0', session: randomUUID() }));
    return;
  }
  if (command === 'SUBSCRIBE') {
    ws.subscriptions.set(headers.id, { id: headers.id, destination: headers.destination });
    return;
  }
  if (command === 'UNSUBSCRIBE') {
    ws.subscriptions.delete(headers.id);
    return;
  }
  if (command !== 'SEND') return;

  let payload = {};
  try { payload = body ? JSON.parse(body) : {}; } catch { return; }

  const senderUsername = ws.username;
  const sender = users[senderUsername];
  if (!sender) return;

  const peerUsername = senderUsername === 'alex' ? 'ghost' : 'alex';

  if (headers.destination === '/app/chat.send') {
    const stored = appendMessage(payload.conversationId, {
      content: payload.content,
      senderId: sender.id,
      clientMessageId: payload.clientMessageId,
      messageType: payload.messageType,
    });

    console.log(`💬 ${senderUsername}: ${payload.content}`);

    // Send ack to sender
    broadcastToUser(senderUsername, '/user/queue/messages',
      envelope('message.ack', { clientMessageId: stored.clientMessageId, message: stored }));

    // Send new message to BOTH users
    broadcastToAll(`/topic/conversation/${stored.conversationId}`,
      envelope('message.new', { message: stored }));
    broadcastToUser(senderUsername, '/user/queue/messages',
      envelope('message.new', { message: stored }));
    broadcastToUser(peerUsername, '/user/queue/messages',
      envelope('message.new', { message: stored }));

    return;
  }

  if (headers.destination === '/app/chat.typing') {
    const frame = envelope(payload.isTyping ? 'typing.started' : 'typing.stopped', {
      conversationId: payload.conversationId,
      userId: sender.id,
      displayName: sender.displayName,
      expiresInMs: 5000,
    });
    // Send typing to the OTHER user only
    broadcastToUser(peerUsername, `/topic/conversation/${payload.conversationId}`, frame);
    broadcastToUser(peerUsername, '/user/queue/events', frame);
    return;
  }

  if (headers.destination === '/app/chat.read' || headers.destination === '/app/chat.delivered') {
    return; // ack silently
  }
}

// -------------------------------------------------------------------------------------
// HTTP + WebSocket wiring
// -------------------------------------------------------------------------------------
const server = createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  try {
    if (url.pathname.startsWith('/api/') || url.pathname === '/actuator/health') {
      return await handleApi(req, res, url);
    }
    res.writeHead(404, { 'Content-Type': 'text/plain' });
    res.end('Multi-user preview harness. Open the frontend at http://localhost:5173\n');
  } catch (error) {
    console.error('error:', error);
    json(res, 500, { status: 500, code: 'PREVIEW_ERROR', message: String(error?.message || error) });
  }
});

const wss = new WebSocketServer({ noServer: true });

server.on('upgrade', (req, socket, head) => {
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  if (url.pathname !== '/ws') { socket.destroy(); return; }

  // Figure out which user this socket belongs to from the cookie
  const cookies = (req.headers.cookie || '').split(';').map((c) => c.trim());
  let sessionId = null;
  for (const c of cookies) {
    if (c.startsWith('preview_session=')) {
      sessionId = c.slice('preview_session='.length);
      break;
    }
  }
  const wsUsername = sessionId ? sessionMap.get(sessionId) : null;

  wss.handleUpgrade(req, socket, head, (ws) => {
    ws.subscriptions = new Map();
    ws.username = wsUsername || 'alex';

    if (!socketsByUser.has(ws.username)) socketsByUser.set(ws.username, new Set());
    socketsByUser.get(ws.username).add(ws);

    console.log(`🔌 ${ws.username} WebSocket connected (${socketsByUser.get(ws.username).size} active)`);

    // Notify the other user that this user came online
    const otherUser = ws.username === 'alex' ? 'ghost' : 'alex';
    broadcastToUser(otherUser, '/user/queue/events',
      envelope('presence.online', { userId: users[ws.username].id, username: ws.username }));

    ws.on('message', (data) => {
      for (const frame of String(data).split('\0')) {
        if (frame.trim()) handleStomp(ws, frame.trim());
      }
    });

    ws.on('close', () => {
      const userSockets = socketsByUser.get(ws.username);
      if (userSockets) {
        userSockets.delete(ws);
        console.log(`🔌 ${ws.username} WebSocket disconnected (${userSockets.size} remaining)`);
        if (userSockets.size === 0) {
          broadcastToUser(otherUser, '/user/queue/events',
            envelope('presence.offline', { userId: users[ws.username].id, username: ws.username }));
        }
      }
    });
    ws.on('error', () => {
      const userSockets = socketsByUser.get(ws.username);
      if (userSockets) userSockets.delete(ws);
    });
  });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log('');
  console.log('═══════════════════════════════════════════════════');
  console.log('  REALCHAT — Multi-user preview');
  console.log('═══════════════════════════════════════════════════');
  console.log(`  Server:  http://localhost:${PORT}`);
  console.log('');
  console.log('  Window 1 (normal)   → sign in as "alex"');
  console.log('  Window 2 (incognito)→ sign in as "ghost"');
  console.log('');
  console.log('  They share one conversation and can chat');
  console.log('  in real time — messages, typing indicators,');
  console.log('  and presence all work across both sessions.');
  console.log('═══════════════════════════════════════════════════');
  console.log('');
});
