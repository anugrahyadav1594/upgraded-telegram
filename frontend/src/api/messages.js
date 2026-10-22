import { http } from './client.js';
import { endpoints } from './endpoints.js';

/**
 * Message history and mutations.
 *
 * History is cursor-based in both directions (`before`/`after`) because the message list paginates on
 * `(created_at, id)` order; offsets would drift as new messages arrive and are forbidden by the product
 * rules. `afterSequence` is the resync path used after a dropped WebSocket connection.
 */
export const messagesApi = {
  history: (conversationId, { before, after, limit = 50 } = {}) =>
    http.get(endpoints.messages(conversationId), { params: { before, after, limit } }),
  resync: (conversationId, afterSequence, limit = 100) =>
    http.get(endpoints.messagesResync(conversationId), { params: { afterSequence, limit } }),
  send: (conversationId, { content, clientMessageId, messageType = 'TEXT' }) =>
    http.post(endpoints.messages(conversationId), { content, clientMessageId, messageType }),
  edit: (messageId, content) => http.patch(endpoints.message(messageId), { content }),
  remove: (messageId) => http.delete(endpoints.message(messageId)),
  markRead: (messageId) => http.post(endpoints.messageRead(messageId)),
  react: (messageId, emoji) => http.post(endpoints.messageReaction(messageId), { emoji }),
  unreact: (messageId, emoji) =>
    http.delete(endpoints.messageReaction(messageId), { params: { emoji } }),
};
