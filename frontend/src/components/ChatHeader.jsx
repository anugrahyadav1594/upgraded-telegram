import { Avatar } from './Avatar.jsx';
import { Icon } from './Icon.jsx';
import { useRealtimeStore } from '../state/realtime.js';
import { fullTimestamp, relativeTime } from '../utils/time.js';

/**
 * Conversation header.
 *
 * Presence is rendered only when the server sent it: a hidden status produces "Presence hidden" rather
 * than a false "offline", because claiming somebody is offline is itself a disclosure.
 */
export function ChatHeader({ peer, conversationId, onToggleDetails, onBack, onOpenProfile, muted, onToggleMute }) {
  const presence = useRealtimeStore((state) => state.presence[peer?.id]);
  const status = presence?.status || (peer?.online ? 'ONLINE' : undefined);

  const statusText = (() => {
    if (!peer) return '';
    if (status === 'ONLINE') return 'Online';
    if (status === 'AWAY') return 'Away';
    if (status === 'HIDDEN') return 'Presence hidden';
    if (presence?.lastSeenAt || peer.lastSeenAt) {
      return `Last seen ${relativeTime(presence?.lastSeenAt || peer.lastSeenAt)}`;
    }
    return `@${peer.username}`;
  })();

  return (
    <header className="thread__header">
      <button
        type="button"
        className="icon-btn mobile-back"
        onClick={onBack}
        aria-label="Back to conversations"
      >
        <Icon name="back" size={20} />
      </button>

      <button
        type="button"
        className="row"
        style={{ background: 'none', border: 'none', cursor: 'pointer', padding: 0, gap: 12 }}
        onClick={onOpenProfile}
      >
        <Avatar user={peer} size={40} presence={status} showPresence />
        <span className="thread__identity" style={{ textAlign: 'left' }}>
          <span className="thread__name">{peer?.displayName || 'Unknown'}</span>
          <span className="thread__presence">{statusText}</span>
        </span>
      </button>

      <div className="thread__actions">
        <button
          type="button"
          className="icon-btn"
          aria-pressed={muted}
          aria-label={muted ? 'Unmute this conversation' : 'Mute this conversation'}
          title={muted ? 'Unmute conversation' : 'Mute conversation'}
          onClick={onToggleMute}
        >
          <Icon name="bell" size={18} />
        </button>
        <button
          type="button"
          className="icon-btn"
          aria-label="Conversation details"
          title="Conversation details"
          onClick={onToggleDetails}
          data-conversation={conversationId}
        >
          <Icon name="settings" size={18} />
        </button>
      </div>
      <span className="sr-only">
        {peer?.displayName ? `${peer.displayName}, ${statusText}` : 'Conversation'}
        {presence?.lastSeenAt ? `, last seen ${fullTimestamp(presence.lastSeenAt)}` : ''}
      </span>
    </header>
  );
}

export default ChatHeader;
