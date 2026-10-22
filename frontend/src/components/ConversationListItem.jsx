import { Avatar } from './Avatar.jsx';
import { relativeTime } from '../utils/time.js';
import { useRealtimeStore } from '../state/realtime.js';

/**
 * One row in the conversation list.
 *
 * The unread badge comes from the server's `unreadCount` (it is authoritative and survives reloads);
 * the realtime store only ever removes it, because a frame telling us a message arrived is not proof
 * that the count the server holds has changed.
 */
export function ConversationListItem({ conversation, currentUserId, onSelect, selected }) {
  const presence = useRealtimeStore((state) => state.presence[conversation.peer?.id]);
  const online = presence?.status === 'ONLINE' || conversation.peer?.online;

  const lastMessage = conversation.lastMessage;
  const preview = (() => {
    if (!lastMessage) return 'No messages yet';
    if (lastMessage.deleted) return 'Message deleted';
    if (lastMessage.senderId === currentUserId) return `You: ${lastMessage.preview || ''}`.trim();
    return lastMessage.preview || 'Sent an attachment';
  })();

  const unread = conversation.unreadCount || 0;

  return (
    <li>
      <button
        type="button"
        className="conversation-item"
        aria-current={selected ? 'true' : undefined}
        onClick={() => onSelect(conversation)}
      >
        <Avatar
          user={conversation.peer}
          presence={online ? 'ONLINE' : 'OFFLINE'}
          showPresence
          size={44}
        />
        <span className="conversation-item__body">
          <span className="conversation-item__row">
            <span className="conversation-item__name">
              {conversation.peer?.displayName || conversation.peer?.username || 'Unknown'}
            </span>
            {lastMessage?.createdAt ? (
              <span className="conversation-item__time">{relativeTime(lastMessage.createdAt)}</span>
            ) : null}
          </span>
          <span className="conversation-item__row">
            <span className="conversation-item__preview">{preview}</span>
          </span>
        </span>
        <span className="conversation-item__badges">
          {conversation.muted ? (
            <span className="badge badge--muted" title="Muted">
              Muted
            </span>
          ) : null}
          {unread > 0 ? (
            <span className="badge" aria-label={`${unread} unread messages`}>
              {unread > 99 ? '99+' : unread}
            </span>
          ) : null}
        </span>
      </button>
    </li>
  );
}

export default ConversationListItem;
