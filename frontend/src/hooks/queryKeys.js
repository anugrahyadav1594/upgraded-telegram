/**
 * React Query key factory.
 *
 * Centralised keys make invalidation precise: a `message.new` frame invalidates exactly the message
 * list for one conversation instead of the whole cache, which keeps a busy chat from refetching the
 * sidebar on every keystroke elsewhere.
 */
export const queryKeys = {
  session: ['session'],
  conversations: {
    all: ['conversations'],
    list: (cursor) => ['conversations', 'list', cursor ?? 'first'],
    detail: (id) => ['conversations', 'detail', id],
  },
  messages: {
    list: (conversationId) => ['messages', conversationId],
    history: (conversationId, direction) => ['messages', conversationId, 'history', direction],
  },
  profiles: {
    detail: (username) => ['profiles', username],
    search: (term) => ['profiles', 'search', term],
  },
  notifications: {
    all: ['notifications'],
    list: (unreadOnly) => ['notifications', 'list', unreadOnly ? 'unread' : 'all'],
    settings: ['notifications', 'settings'],
  },
  privacy: ['privacy'],
  blocks: ['blocks'],
  sessions: ['sessions'],
};
