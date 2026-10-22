import { useState } from 'react';

import { Icon } from './Icon.jsx';
import { useSessionStore } from '../state/session.js';

/**
 * Shown when the application cannot reach its backend.
 *
 * This screen exists because the alternative — an indefinite "Checking your session…" spinner — is the
 * single most confusing thing a web app can do: the user cannot tell whether the app is slow, broken, or
 * whether they are supposed to do something. Here the message names the likely cause (the API is not
 * running), offers a retry, and stays honest about what it does not know.
 *
 * The address is shown because in development the answer is almost always "start the backend on 8080".
 */
export function SessionUnavailable() {
  const error = useSessionStore((state) => state.error);
  const retry = useSessionStore((state) => state.retry);
  const [checking, setChecking] = useState(false);

  const apiBase = import.meta.env.VITE_API_BASE || 'this origin';
  const detail = {
    TIMEOUT: 'The server accepted the connection but never answered.',
    NETWORK_ERROR: 'The connection was refused — which usually means nothing is listening.',
    BAD_GATEWAY:
      'Something answered, but it was not the API. If you started only the frontend, the backend is probably not running yet.',
    SERVICE_UNAVAILABLE: 'The API is not ready yet. It may still be starting up.',
    GATEWAY_TIMEOUT: 'The request reached the server and then timed out.',
  }[error?.code] || error?.message || 'The server could not be reached.';

  async function tryAgain() {
    setChecking(true);
    try {
      await retry();
    } finally {
      setChecking(false);
    }
  }

  return (
    <main className="auth-page">
      <div className="auth-card">
        <div className="brand">
          <span className="brand__dot" aria-hidden="true" />
          REALCHAT
        </div>
        <h1>Can’t reach the server</h1>
        <p className="auth-card__subtitle">{detail}</p>

        <div className="alert alert--error" role="alert">
          <p style={{ margin: 0 }}>{error?.message || 'The request failed.'}</p>
        </div>

        <div className="card" style={{ boxShadow: 'none', borderColor: 'var(--border-strong)' }}>
          <p className="text-sm" style={{ marginBottom: 8 }}>
            <strong>Is the API running?</strong> The frontend talks to <code>{apiBase}</code>. In
            development the backend must be listening on <code>http://localhost:8080</code> — start it
            with:
          </p>
          <pre className="text-xs" style={{ margin: 0, whiteSpace: 'pre-wrap' }}>
            cd backend && ./mvnw spring-boot:run
          </pre>
          <p className="text-xs muted" style={{ marginBottom: 0, marginTop: 8 }}>
            Or run everything in Docker:{' '}
            <code>docker compose -f infrastructure/docker-compose.yml up -d</code>. See RUNNING.md.
          </p>
        </div>

        <button type="button" className="btn btn--block" onClick={tryAgain} disabled={checking}>
          {checking ? <span className="spinner" /> : <Icon name="refresh" size={16} />}
          {checking ? 'Checking…' : 'Try again'}
        </button>
      </div>
    </main>
  );
}

export default SessionUnavailable;
