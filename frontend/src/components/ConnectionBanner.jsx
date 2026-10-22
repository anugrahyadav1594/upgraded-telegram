import { useOnlineStatus } from '../hooks/useOnlineStatus.js';
import { useRealtimeStore } from '../state/realtime.js';
import { ConnectionState } from '../websocket/stompClient.js';
import { Icon } from './Icon.jsx';

/**
 * Connection truth.
 *
 * Three distinct situations must be distinguishable, because the right user action differs for each:
 * the browser is offline (wait), the socket is reconnecting (wait, messages will resync), and the
 * socket has failed permanently (reload or sign in again). Collapsing them into one "offline" label
 * would hide a required action.
 */
export function ConnectionBanner() {
  const online = useOnlineStatus();
  const connection = useRealtimeStore((state) => state.connection);
  const attempt = useRealtimeStore((state) => state.reconnectAttempt);

  if (!online) {
    return (
      <div className="banner banner--warning" role="status">
        <span className="row">
          <Icon name="alert" size={16} />
          You are offline. Messages will send once your connection returns.
        </span>
      </div>
    );
  }

  if (connection === ConnectionState.RECONNECTING) {
    return (
      <div className="banner banner--warning" role="status">
        <span className="row">
          <Icon name="refresh" size={16} />
          Reconnecting… (attempt {attempt}). New messages will appear once the connection is back.
        </span>
      </div>
    );
  }

  if (connection === ConnectionState.FAILED) {
    return (
      <div className="banner banner--danger" role="alert">
        <span className="row">
          <Icon name="alert" size={16} />
          Live updates stopped. Reload the page to reconnect.
        </span>
        <button type="button" className="btn btn--secondary" onClick={() => window.location.reload()}>
          Reload
        </button>
      </div>
    );
  }

  return null;
}

export default ConnectionBanner;
