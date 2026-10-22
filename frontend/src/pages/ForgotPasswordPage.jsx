import { useState } from 'react';
import { Link } from 'react-router-dom';

import { authApi } from '../api/auth.js';
import { useDocumentTitle } from '../hooks/useDocumentTitle.js';

/**
 * Password reset request.
 *
 * The response is always the same, whether or not the address exists — otherwise this endpoint becomes
 * an account-existence oracle. The form therefore always reports "check your email".
 */
export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  useDocumentTitle('Reset password');

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await authApi.forgotPassword({ email: email.trim().toLowerCase() });
      setSent(true);
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
        <h1>Reset your password</h1>

        {sent ? (
          <>
            <p className="alert alert--success" role="status">
              If an account exists for that address, a reset link is on its way. The link works once and
              expires in 30 minutes.
            </p>
            <p className="auth-card__footer">
              <Link to="/login">Back to sign in</Link>
            </p>
          </>
        ) : (
          <>
            <p className="auth-card__subtitle">
              Enter the email address on your account. We will send a single-use link.
            </p>
            {error ? (
              <p className="alert alert--error" role="alert">
                {error.message}
              </p>
            ) : null}
            <form onSubmit={submit} noValidate>
              <div className="field">
                <label className="field__label" htmlFor="email">
                  Email address
                </label>
                <input
                  id="email"
                  type="email"
                  autoComplete="email"
                  required
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                />
              </div>
              <button type="submit" className="btn btn--block" disabled={busy || !email}>
                {busy ? 'Sending…' : 'Send reset link'}
              </button>
            </form>
            <p className="auth-card__footer">
              <Link to="/login">Back to sign in</Link>
            </p>
          </>
        )}
      </div>
    </main>
  );
}

export default ForgotPasswordPage;
