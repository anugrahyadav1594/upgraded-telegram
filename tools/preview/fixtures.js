/**
 * Fixture data for the preview harness.
 *
 * Shapes are copied from `docs/api-specification.md` and the DTOs in the backend
 * (`conversations/dto`, `messaging/dto`, `notifications/dto`): if a field name here disagrees with the
 * API, the harness is wrong, not the API. Keeping them in one file makes that reviewable.
 *
 * This is deliberately *not* a test fixture. It is the content of a demo: three conversations in
 * different states (one unread, one muted, one quiet), message states covering everything the UI can
 * render (sent, delivered, read, edited, deleted, reacted, with a reply), and presence in more than one
 * state. A screenshot of the happy path alone would hide most of the interface.
 */

const now = Date.now();
const minutesAgo = (n) => new Date(now - n * 60_000).toISOString();

export const me = {
  id: '11111111-1111-4111-8111-111111111111',
  email: 'alex@example.test',
  username: 'alex',
  displayName: 'Alex Rivera',
  avatarUrl: null,
  bio: 'Backend developer. Mostly here for the group chats.',
  usernamePublic: true,
  emailVerified: true,
  profileComplete: true,
  privacy: {
    showOnlineStatus: true,
    showLastSeen: true,
    showProfileInSearch: true,
    allowMessagesFrom: 'EVERYONE',
    readReceipts: true,
  },
  unreadNotifications: 2,
  createdAt: minutesAgo(60 * 24 * 30),
};

export const peers = {
  ghost: {
    id: '22222222-2222-4222-8222-222222222222',
    username: 'ghost',
    displayName: 'Ghost',
    avatarUrl: null,
    online: true,
    lastSeenAt: null,
  },
  developer: {
    id: '33333333-3333-4333-8333-333333333333',
    username: 'developer',
    displayName: 'Sam Dev',
    avatarUrl: null,
    online: false,
    lastSeenAt: minutesAgo(47),
  },
  testuser: {
    id: '44444444-4444-4444-8444-444444444444',
    username: 'testuser',
    displayName: 'Test User',
    avatarUrl: null,
    online: false,
    lastSeenAt: minutesAgo(60 * 5),
  },
};

export const conversations = [
  {
    id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    type: 'DIRECT',
    peer: peers.ghost,
    lastMessage: {
      id: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2',
      senderId: peers.ghost.id,
      preview: 'did you get the deployment working?',
      messageType: 'TEXT',
      createdAt: minutesAgo(2),
      deleted: false,
    },
    unreadCount: 1,
    muted: false,
    archived: false,
    lastActivityAt: minutesAgo(2),
    createdAt: minutesAgo(60 * 24 * 3),
  },
  {
    id: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
    type: 'DIRECT',
    peer: peers.developer,
    lastMessage: {
      id: 'dddddddd-dddd-4ddd-8ddd-ddddddddddd5',
      senderId: me.id,
      preview: 'Pushed the migration, take a look when you can',
      messageType: 'TEXT',
      createdAt: minutesAgo(38),
      deleted: false,
    },
    unreadCount: 0,
    muted: false,
    archived: false,
    lastActivityAt: minutesAgo(38),
    createdAt: minutesAgo(60 * 24 * 9),
  },
  {
    id: 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',
    type: 'DIRECT',
    peer: peers.testuser,
    lastMessage: {
      id: 'ffffffff-ffff-4fff-8fff-fffffffffff3',
      senderId: peers.testuser.id,
      preview: 'thanks, that fixed it',
      messageType: 'TEXT',
      createdAt: minutesAgo(60 * 26),
      deleted: false,
    },
    unreadCount: 0,
    muted: true,
    archived: false,
    lastActivityAt: minutesAgo(60 * 26),
    createdAt: minutesAgo(60 * 24 * 21),
  },
];

/** Messages for the conversation with @ghost — every state the UI can draw, in one thread. */
export const messages = [
  {
    id: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb0',
    conversationId: conversations[0].id,
    senderId: me.id,
    content: 'Hey — pushed the realtime layer last night. Presence and typing are Redis-only, never touch Postgres.',
    messageType: 'TEXT',
    clientMessageId: null,
    sequence: 101,
    replyTo: null,
    reactions: [],
    receipt: { status: 'READ', deliveredAt: minutesAgo(58), readAt: minutesAgo(57) },
    editedAt: null,
    deletedAt: null,
    createdAt: minutesAgo(59),
    updatedAt: minutesAgo(59),
  },
  {
    id: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1',
    conversationId: conversations[0].id,
    senderId: peers.ghost.id,
    content: 'Nice. Does the resync actually work if the socket drops mid-conversation?',
    messageType: 'TEXT',
    clientMessageId: null,
    sequence: 102,
    replyTo: null,
    reactions: [{ emoji: '👍', count: 1, reactedByMe: false }],
    receipt: null,
    editedAt: null,
    deletedAt: null,
    createdAt: minutesAgo(24),
    updatedAt: minutesAgo(24),
  },
  {
    id: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb3',
    senderId: null,
    conversationId: conversations[0].id,
    content: null,
    messageType: 'TEXT',
    clientMessageId: null,
    sequence: 103,
    replyTo: null,
    reactions: [],
    receipt: null,
    editedAt: null,
    deletedAt: minutesAgo(20),
    createdAt: minutesAgo(21),
    updatedAt: minutesAgo(20),
  },
  {
    id: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb4',
    conversationId: conversations[0].id,
    senderId: me.id,
    content: 'Yes — after every reconnect the client asks for everything after its highest sequence, so nothing is lost. I deleted my half-finished explanation above.',
    messageType: 'TEXT',
    clientMessageId: null,
    sequence: 104,
    replyTo: { id: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', senderId: peers.ghost.id, preview: 'Does the resync actually work…', deleted: false },
    reactions: [
      { emoji: '❤️', count: 2, reactedByMe: true },
      { emoji: '😂', count: 1, reactedByMe: false },
    ],
    receipt: { status: 'DELIVERED', deliveredAt: minutesAgo(12), readAt: null },
    editedAt: minutesAgo(12),
    deletedAt: null,
    createdAt: minutesAgo(13),
    updatedAt: minutesAgo(12),
  },
  {
    id: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2',
    conversationId: conversations[0].id,
    senderId: peers.ghost.id,
    content: 'did you get the deployment working?',
    messageType: 'TEXT',
    clientMessageId: null,
    sequence: 105,
    replyTo: null,
    reactions: [],
    receipt: null,
    editedAt: null,
    deletedAt: null,
    createdAt: minutesAgo(2),
    updatedAt: minutesAgo(2),
  },
];

export const notifications = [
  {
    id: '99999999-9999-4999-8999-999999999991',
    type: 'NEW_MESSAGE',
    conversationId: conversations[0].id,
    messageId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2',
    actor: { id: peers.ghost.id, username: 'ghost', displayName: 'Ghost', avatarUrl: null },
    preview: 'did you get the deployment working?',
    read: false,
    createdAt: minutesAgo(2),
  },
  {
    id: '99999999-9999-4999-8999-999999999992',
    type: 'CONVERSATION_UPDATE',
    conversationId: conversations[1].id,
    messageId: null,
    actor: { id: peers.developer.id, username: 'developer', displayName: 'Sam Dev', avatarUrl: null },
    preview: 'Sam Dev started a conversation with you',
    read: false,
    createdAt: minutesAgo(38),
  },
  {
    id: '99999999-9999-4999-8999-999999999993',
    type: 'MODERATION_UPDATE',
    conversationId: null,
    messageId: null,
    actor: null,
    preview: 'Your report was reviewed — thanks for flagging it.',
    read: true,
    createdAt: minutesAgo(60 * 20),
  },
];

export const notificationSettings = {
  newMessage: true,
  mentions: true,
  conversationUpdates: true,
  moderationUpdates: true,
  emailDigest: false,
};

export const privacy = me.privacy;

export const blocks = [
  {
    userId: '55555555-5555-4555-8555-555555555555',
    username: 'spammer',
    displayName: 'Nothing To See',
    avatarUrl: null,
    blockedAt: minutesAgo(60 * 48),
  },
];

export const sessions = [
  {
    id: '77777777-7777-4777-8777-777777777771',
    current: true,
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)',
    ipAddress: '203.0.113.10',
    createdAt: minutesAgo(60 * 6),
    lastUsedAt: minutesAgo(0),
    expiresAt: new Date(now + 30 * 24 * 60 * 60_000).toISOString(),
  },
  {
    id: '77777777-7777-4777-8777-777777777772',
    current: false,
    userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0)',
    ipAddress: '203.0.113.44',
    createdAt: minutesAgo(60 * 24 * 4),
    lastUsedAt: minutesAgo(60 * 9),
    expiresAt: new Date(now + 26 * 24 * 60 * 60_000).toISOString(),
  },
];

export function publicProfile(username) {
  const peer =
    peers[username] ||
    (username === me.username
      ? { id: me.id, username: me.username, displayName: me.displayName, avatarUrl: null, online: true }
      : null);
  if (!peer) return null;
  return {
    id: peer.id,
    username: peer.username,
    usernamePublic: true,
    displayName: peer.displayName,
    avatarUrl: null,
    bio:
      peer.username === 'ghost'
        ? 'Lurker. Reads everything, replies eventually.'
        : peer.username === 'developer'
          ? 'Builds things, breaks things, fixes things.'
          : 'Just here to test the privacy settings.',
    online: peer.online,
    lastSeenAt: peer.lastSeenAt,
    memberSince: minutesAgo(60 * 24 * 60),
    existingConversationId: conversations.find((c) => c.peer.username === peer.username)?.id || null,
    blockedByMe: false,
    canMessage: true,
  };
}
