import { useNotificationStore } from '../state/notifications.js';
import { Icon } from './Icon.jsx';

/**
 * Transient messages.
 *
 * The region is a polite live region so a screen reader announces problems without interrupting what
 * the user is typing; toasts are dismissible and never block the composer.
 */
export function Toasts() {
  const toasts = useNotificationStore((state) => state.toasts);
  const dismiss = useNotificationStore((state) => state.dismissToast);

  if (toasts.length === 0) return null;

  return (
    <div className="toasts" role="status" aria-live="polite" aria-atomic="false">
      {toasts.map((toast) => (
        <div key={toast.id} className={`toast toast--${toast.tone || 'info'}`}>
          <div>
            <div className="toast__title">{toast.title}</div>
            {toast.body ? <div className="toast__body">{toast.body}</div> : null}
          </div>
          <button
            type="button"
            className="icon-btn"
            onClick={() => dismiss(toast.id)}
            aria-label={`Dismiss: ${toast.title}`}
          >
            <Icon name="close" size={16} />
          </button>
        </div>
      ))}
    </div>
  );
}

export default Toasts;
