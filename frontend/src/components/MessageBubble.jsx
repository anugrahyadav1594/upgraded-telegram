import { useState } from 'react';

import { Avatar } from './Avatar.jsx';
import { Icon } from './Icon.jsx';
import { clockTime, fullTimestamp, isoTime } from '../utils/time.js';
import { MAX_MESSAGE_LENGTH } from '../utils/validation.js';

const QUICK_REACTIONS = ['👍', '❤️', '😂', '😮', '😢', '🙏'];

const RECEIPT_ICON = {
  SENDING: 'clock',
  SENT: 'check',
  DELIVERED: 'checkDouble',
  READ: 'checkDouble',
  FAILED: 'alert',
};

const RECEIPT_LABEL = {
  SENDING: 'Sending',
  SENT: 'Sent',
  DELIVERED: 'Delivered',
  READ: 'Read',
  FAILED: 'Not sent',
};

/**
 * One message.
 *
 * The bubble renders whichever state is most truthful: an optimistic entry from the outbox while the
 * server has not answered, and the server's own row (including its receipt) once it has. A deleted
 * message renders as a tombstone with no content — the server sends `content: null` and the client is
 * not given the text to leak back.
 */
export function MessageBubble({
  message,
  optimistic,
  currentUserId,
  peer,
  showSender = false,
  onRetry,
  onEdit,
  onDelete,
  onReact,
  onReport,
  editing,
}) {
  const [pickerOpen, setPickerOpen] = useState(false);
  const [draft, setDraft] = useState(message.content || '');

  const isOwn = (optimistic ? true : message.senderId === currentUserId);
  const state = optimistic?.state;
  const deleted = Boolean(message.deletedAt) || message.deleted;
  const content = deleted ? null : message.content;

  const receiptStatus = optimistic ? state : message.receipt?.status;
  const edited = Boolean(message.editedAt);
  const reactions = message.reactions || [];
  const canEdit =
    isOwn && !deleted && !optimistic && message.messageType === 'TEXT' && message.editable !== false;

  function submitEdit(event) {
    event.preventDefault();
    const next = draft.trim();
    if (!next) return;
    onEdit?.({ messageId: message.id, content: next });
  }

  return (
    <article
      className={[
        'message',
        isOwn ? 'message--out' : 'message--in',
        optimistic?.state === 'FAILED' ? 'message--failed' : '',
        optimistic && optimistic.state !== 'FAILED' ? 'message--pending' : '',
      ]
        .filter(Boolean)
        .join(' ')}
      aria-label={`${isOwn ? 'You' : peer?.displayName || 'They'} at ${fullTimestamp(
        message.createdAt,
      )}: ${deleted ? 'message deleted' : message.content || ''}`}
    >
      {!isOwn ? <Avatar user={peer} size={32} /> : null}

      <div>
        {showSender && !isOwn ? (
          <div className="message__sender">{peer?.displayName || 'Unknown'}</div>
        ) : null}

        <div className="message__body">
          {deleted ? (
            <span className="message__tombstone">This message was deleted</span>
          ) : editing ? (
            <form onSubmit={submitEdit} className="stack" style={{ gap: 8, minWidth: 220 }}>
              <label className="sr-only" htmlFor={`edit-${message.id}`}>
                Edit message
              </label>
              <textarea
                id={`edit-${message.id}`}
                rows={2}
                maxLength={MAX_MESSAGE_LENGTH}
                value={draft}
                onChange={(event) => setDraft(event.target.value)}
              />
              <div className="row" style={{ gap: 8 }}>
                <button type="submit" className="btn">
                  Save
                </button>
                <button
                  type="button"
                  className="btn btn--secondary"
                  onClick={() => {
                    setDraft(message.content || '');
                    onEdit?.(null);
                  }}
                >
                  Cancel
                </button>
              </div>
            </form>
          ) : (
            content
          )}
        </div>

        {reactions.length > 0 ? (
          <div className="reaction-row">
            {reactions.map((reaction) => (
              <button
                key={reaction.emoji}
                type="button"
                className="reaction-chip"
                aria-pressed={reaction.reactedByMe}
                aria-label={`${reaction.emoji} ${reaction.count} reaction${
                  reaction.count === 1 ? '' : 's'
                }${reaction.reactedByMe ? ', including yours' : ''}`}
                onClick={() =>
                  onReact?.({ messageId: message.id, emoji: reaction.emoji, reacted: reaction.reactedByMe })
                }
              >
                <span aria-hidden="true">{reaction.emoji}</span>
                <span>{reaction.count}</span>
              </button>
            ))}
          </div>
        ) : null}

        <div className="message__meta">
          <time dateTime={isoTime(message.createdAt)}>{clockTime(message.createdAt)}</time>
          {edited ? <span className="message__edited">edited</span> : null}
          {isOwn && receiptStatus ? (
            <span
              className={`message__receipt${
                receiptStatus === 'READ' ? ' message__receipt--read' : ''
              }${receiptStatus === 'FAILED' ? ' message__receipt--failed' : ''}`}
              title={RECEIPT_LABEL[receiptStatus]}
            >
              <Icon name={RECEIPT_ICON[receiptStatus] || 'check'} size={14} />
              <span className="sr-only">{RECEIPT_LABEL[receiptStatus]}</span>
            </span>
          ) : null}
          {optimistic?.state === 'FAILED' ? (
            <button
              type="button"
              className="btn btn--ghost text-xs"
              onClick={() => onRetry?.(optimistic.clientMessageId)}
            >
              <Icon name="refresh" size={14} /> Retry
            </button>
          ) : null}
        </div>
      </div>

      {!deleted && !optimistic ? (
        <div className="message__actions">
          <button
            type="button"
            className="icon-btn"
            aria-label="Add reaction"
            aria-expanded={pickerOpen}
            onClick={() => setPickerOpen((open) => !open)}
          >
            <span aria-hidden="true">🙂</span>
          </button>
          {canEdit ? (
            <button
              type="button"
              className="icon-btn"
              aria-label="Edit message"
              onClick={() => onEdit?.({ start: true, messageId: message.id })}
            >
              <Icon name="check" size={16} />
            </button>
          ) : null}
          {isOwn ? (
            <button
              type="button"
              className="icon-btn"
              aria-label="Delete message"
              onClick={() => onDelete?.({ messageId: message.id })}
            >
              <Icon name="close" size={16} />
            </button>
          ) : (
            <button
              type="button"
              className="icon-btn"
              aria-label="Report message"
              onClick={() => onReport?.({ messageId: message.id })}
            >
              <Icon name="flag" size={16} />
            </button>
          )}
        </div>
      ) : null}

      {pickerOpen ? (
        <div className="emoji-picker" role="group" aria-label="Choose a reaction">
          {QUICK_REACTIONS.map((emoji) => (
            <button
              key={emoji}
              type="button"
              onClick={() => {
                setPickerOpen(false);
                onReact?.({ messageId: message.id, emoji, reacted: false });
              }}
            >
              <span aria-hidden="true">{emoji}</span>
              <span className="sr-only">React with {emoji}</span>
            </button>
          ))}
        </div>
      ) : null}
    </article>
  );
}

export default MessageBubble;
