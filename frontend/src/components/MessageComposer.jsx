import { useEffect, useRef, useState } from 'react';

import { Icon } from './Icon.jsx';
import { useAutosize } from '../hooks/useAutosize.js';
import { useTypingPublisher } from '../hooks/useTypingIndicator.js';
import { MAX_MESSAGE_LENGTH, validateMessage } from '../utils/validation.js';

/**
 * The composer.
 *
 * Enter sends, Shift+Enter starts a new line — the convention users already have in every messenger.
 * The counter appears only near the limit so the interface stays quiet the rest of the time. Send is
 * disabled while a send is in flight, which keeps "did I send it twice?" questions from existing; the
 * message itself is never lost because it is already in the outbox.
 */
export function MessageComposer({ conversationId, onSend, disabled, disabledReason, replyTo, onClearReply }) {
  const [value, setValue] = useState('');
  const [error, setError] = useState(null);
  const textareaRef = useRef(null);
  const { notifyKeystroke, stopTyping } = useTypingPublisher(conversationId);
  const { ref: autosizeRef } = useAutosize({ maxHeight: 160 });

  useEffect(() => {
    const node = textareaRef.current;
    if (node) autosizeRef.current = node;
  }, [autosizeRef, replyTo]);

  function submit(event) {
    event?.preventDefault();
    const problem = validateMessage(value);
    if (problem) {
      setError(problem);
      return;
    }
    setError(null);
    onSend(value.trim());
    setValue('');
    stopTyping();
    const node = textareaRef.current;
    if (node) {
      node.style.height = 'auto';
      node.focus();
    }
  }

  function handleKeyDown(event) {
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      submit();
    }
  }

  function handleChange(event) {
    setValue(event.target.value);
    if (error) setError(null);
    notifyKeystroke();
  }

  const length = value.length;
  const counterTone =
    length > MAX_MESSAGE_LENGTH ? 'composer__counter--over' : length > MAX_MESSAGE_LENGTH * 0.9 ? 'composer__counter--warn' : '';
  const overLimit = length > MAX_MESSAGE_LENGTH;

  return (
    <form className="composer" onSubmit={submit}>
      {replyTo ? (
        <div className="composer__reply">
          <span>
            Replying to <strong>{replyTo.displayName || 'message'}</strong>
          </span>
          <button type="button" className="icon-btn" onClick={onClearReply} aria-label="Cancel reply">
            <Icon name="close" size={16} />
          </button>
        </div>
      ) : null}

      {disabled ? (
        <p className="text-sm muted" role="status">
          {disabledReason || 'You cannot send messages in this conversation.'}
        </p>
      ) : (
        <>
          <div className="composer__row">
            <label className="sr-only" htmlFor="composer-input">
              Message
            </label>
            <textarea
              id="composer-input"
              ref={textareaRef}
              rows={1}
              value={value}
              maxLength={MAX_MESSAGE_LENGTH + 1}
              placeholder="Write a message…"
              aria-describedby={error ? 'composer-error' : 'composer-hint'}
              aria-invalid={overLimit || Boolean(error)}
              onChange={handleChange}
              onKeyDown={handleKeyDown}
              onBlur={stopTyping}
              onPaste={(event) => {
                // Pasted text is still subject to the same limit; trimming here is friendlier than
                // letting the server reject a 40 kB paste.
                const pasted = event.clipboardData?.getData('text') || '';
                if (pasted.length > MAX_MESSAGE_LENGTH) {
                  event.preventDefault();
                  setValue(pasted.slice(0, MAX_MESSAGE_LENGTH));
                  setError('Pasted text was shortened to the maximum message length.');
                }
              }}
            />
            <button
              type="submit"
              className="btn"
              disabled={overLimit || value.trim().length === 0}
              aria-label="Send message"
            >
              <Icon name="send" size={18} />
            </button>
          </div>
          {error ? (
            <p className="field__error" id="composer-error" role="alert">
              <Icon name="alert" size={14} /> {error}
            </p>
          ) : (
            <p className="sr-only" id="composer-hint">
              Press Enter to send, Shift and Enter for a new line.
            </p>
          )}
          {length > MAX_MESSAGE_LENGTH * 0.9 ? (
            <div className={`composer__counter ${counterTone}`} aria-live="polite">
              {length}/{MAX_MESSAGE_LENGTH}
            </div>
          ) : null}
        </>
      )}
    </form>
  );
}

export default MessageComposer;
