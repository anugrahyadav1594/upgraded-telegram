import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';

import { useSessionStore } from '../state/session.js';
import { useDocumentTitle } from '../hooks/useDocumentTitle.js';
import { Icon } from '../components/Icon.jsx';

/**
 * Sign in.
 *
 * The error shown is the server's, not a guess: a single generic message for bad credentials (never
 * "that account exists but the password is wrong"), and a distinct message for rate limiting so the user
 * understands why they cannot try again yet.
 */
export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const login = useSessionStore((state) => state.login);
  const [identifier, setIdentifier] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  useDocumentTitle('Sign in');

  const redirectTo = location.state?.from?.pathname || '/';

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const user = await login({ identifier: identifier.trim(), password });
      navigate(user?.profileComplete === false ? '/onboarding' : redirectTo, { replace: true });
    } catch (submitError) {
      setError(submitError);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="auth-page">
      <div className="auth-card">
        <div className="brand">
          <span className="brand__dot" aria-hidden="true" />
          REALCHAT
        </div>
        <h1>Welcome back</h1>
        <p className="auth-card__subtitle">
          Sign in with your email address or your username. Your handle is what other people see — your
          email is never shown to anyone.
        </p>

        {error ? (
          <p className="alert alert--error" role="alert">
            {error.message}
            {error.isRateLimited ? ' Try again in a few minutes.' : ''}
          </p>
        ) : null}

        <form onSubmit={submit} noValidate>
          <div className="field">
            <label className="field__label" htmlFor="identifier">
              Email or username
            </label>
            <input
              id="identifier"
              name="identifier"
              autoComplete="username"
              autoFocus
              required
              maxLength={254}
              value={identifier}
              onChange={(event) => setIdentifier(event.target.value)}
              aria-invalid={Boolean(error)}
            />
          </div>

          <div className="field">
            <label className="field__label" htmlFor="password">
              Password
            </label>
            <input
              id="password"
              name="password"
              type="password"
              autoComplete="current-password"
              required
              maxLength={128}
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              aria-invalid={Boolean(error)}
            />
          </div>

          <button type="submit" className="btn btn--block" disabled={busy || !identifier || !password}>
            {busy ? 'Signing in…' : 'Sign in'}
          </button>
        </form>

        <p className="auth-card__footer">
          <Link to="/forgot-password">Forgot your password?</Link>
          <br />
          New here? <Link to="/register">Create an account</Link>
        </p>

        {error?.isNetworkError ? (
          <p className="field__error" role="status">
            <Icon name="alert" size={14} /> The server could not be reached.
          </p>
        ) : null}
      </div>
    </main>
  );
}

export default LoginPage;
