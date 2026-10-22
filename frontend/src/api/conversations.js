import { http } from './client.js';
import { endpoints } from './endpoints.js';

/**
 * Conversation calls.
 *
 * `createConversation` takes a username, never a conversation id supplied by the caller, so a client
 * cannot fabricate access to a thread it is not part of — the server resolves the peer and decides.
 */
export const conversationsApi = {
  list: (cursor, limit = 30) => http.get(endpoints.conversations(), { params: { cursor, limit } }),

  /**
   * Starts (or returns) the direct conversation with `peerUserId`.
   *
   * The peer is addressed by id, never by a conversation id supplied by the caller: ids are untrusted,
   * and a conversation is identified by the pair of participants the server derives itself.
   * The server answers 201 when it created the thread and 200 when it already existed; both are success.
   */
  create: async (peerUserId) => http.post(endpoints.conversations(), { peerUserId }),
  get: (conversationId) => http.get(endpoints.conversation(conversationId)),
  archive: (conversationId, archived = true) =>
    http.patch(endpoints.conversation(conversationId), { archived }),
  mute: (conversationId, muted = true) => http.patch(endpoints.conversation(conversationId), { muted }),
  remove: (conversationId) => http.delete(endpoints.conversation(conversationId)),
  /** Read receipts are a watermark: the server converts the message id into a sequence itself. */
  markRead: (conversationId, lastMessageId) =>
    http.post(endpoints.conversationRead(conversationId), { lastMessageId }),
};
