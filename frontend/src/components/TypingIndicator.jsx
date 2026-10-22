import { useTypingNames } from '../hooks/useTypingIndicator.js';

/**
 * "Alex is typing…"
 *
 * Rendered from the ephemeral store only. The line keeps its height when nobody is typing so the
 * message list does not jump every time somebody starts a sentence.
 */
export function TypingIndicator({ conversationId }) {
  const { text } = useTypingNames(conversationId);

  return (
    <div className="typing-line" aria-live="polite">
      {text ? (
        <span className="row" style={{ gap: 6 }}>
          <span className="typing-dots" aria-hidden="true">
            <i />
            <i />
            <i />
          </span>
          {text}
        </span>
      ) : null}
    </div>
  );
}

export default TypingIndicator;
