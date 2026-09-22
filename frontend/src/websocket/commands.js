import { realtimeClient } from './stompClient.js';

/**
 * STOMP command builders.
 *
 * Every frame the client sends is constructed here and nowhere else, so the field names match the
 * server's command records exactly (`isTyping`, `lastMessageId`, `messageType`, …). The server rejects
 * unknown properties, which is the behaviour we want: a typo must fail loudly at development time
 * instead of being silently ignored in production.
 *
 * Commands carry no `senderId`, no `sequence` and no `status`. Those are server-authoritative — the
 * backend derives identity from the session and decides ordering and lifecycle.
 */
export const chatCommands = {
  sendMessage({ conversationId, content, clientMessageId, messageType = 'TEXT', replyToMessageId }) {
    return realtimeClient().publish('/app/chat.send', {
      conversationId,
      content,
      clientMessageId,
      messageType,
      replyToMessageId,
    });
  },

  markDelivered({ conversationId, lastMessageId }) {
    return realtimeClient().publish('/app/chat.delivered', { conversationId, lastMessageId });
  },

  markRead({ conversationId, lastMessageId }) {
    return realtimeClient().publish('/app/chat.read', { conversationId, lastMessageId });
  },

  typing({ conversationId, isTyping }) {
    return realtimeClient().publish('/app/chat.typing', { conversationId, isTyping });
  },

  editMessage({ messageId, content }) {
    return realtimeClient().publish('/app/message.edit', { messageId, content });
  },

  deleteMessage({ messageId }) {
    return realtimeClient().publish('/app/message.delete', { messageId });
  },

  react({ messageId, emoji }) {
    return realtimeClient().publish('/app/message.reaction', { messageId, emoji });
  },
};
