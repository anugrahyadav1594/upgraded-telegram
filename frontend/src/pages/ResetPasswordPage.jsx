import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';

import { authApi } from '../api/auth.js';
import { useDocumentTitle } from '../hooks/useDocumentTitle.js';
import { validatePassword } from '../utils/validation.js';

/**
 * Password reset completion.
 *
 * The token arrives in the URL fragment-free query string because the backend sends a plain link. On
 * success every other session is revoked server-side — the response says so instead of leaving the user
 * to discover it later.
 */
export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const token = params.get('token') || '';
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  useDocumentTitle('Choose a new password');

  const passwordError = validatePassword(password);
  const mismatch = confirm.length > 0 && confirm !== password;

  async function submit(event) {
    event.preventDefault();
    if (passwordError || mismatch) return;
    setBusy(true);
    setError(null);
    try {
      await authApi.resetPassword({ token, newPassword: password });
      navigate('/login', { replace: true, state: { reset: true } });
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
        <h1>Choose a new password</h1>

        {!token ? (
          <>
            <p className="alert alert--error" role="alert">
              This reset link is missing its token. Request a new one.
            </p>
            <p className="auth-card__footer">
              <Link to="/forgot-password">Request a new link</Link>
            </p>
          </>
        ) : (
          <>
            <p className="auth-card__subtitle">
              Setting a new password signs out every other device on this account.
            </p>
            {error ? (
              <p className="alert alert--error" role="alert">
                {error.message}
              </p>
            ) : null}
            <form onSubmit={submit} noValidate>
              <div className="field">
                <label className="field__label" htmlFor="new-password">
                  New password
                </label>
                <input
                  id="new-password"
                  type="password"
                  autoComplete="new-password"
                  required
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  aria-invalid={Boolean(passwordError && password)}
                />
                {password ? <span className="field__hint">{passwordError || 'Looks good.'}</span> : null}
              </div>
              <div className="field">
                <label className="field__label" htmlFor="confirm-password">
                  Confirm new password
                </label>
                <input
                  id="confirm-password"
                  type="password"
                  autoComplete="new-password"
                  required
                  value={confirm}
                  onChange={(event) => setConfirm(event.target.value)}
                  aria-invalid={mismatch}
                />
                {mismatch ? <span className="field__error">Passwords do not match.</span> : null}
              </div>
              <button
                type="submit"
                className="btn btn--block"
                disabled={busy || Boolean(passwordError) || mismatch || !confirm}
              >
                {busy ? 'Saving…' : 'Set new password'}
              </button>
            </form>
          </>
        )}
      </div>
    </main>
  );
}

export default ResetPasswordPage;
