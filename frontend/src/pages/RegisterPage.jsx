import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';

import { useSessionStore } from '../state/session.js';
import { useDocumentTitle } from '../hooks/useDocumentTitle.js';
import { validateDisplayName, validateEmail, validatePassword, validateUsername } from '../utils/validation.js';
import { Icon } from '../components/Icon.jsx';

/**
 * Registration.
 *
 * Usernames are pseudonymous by design: the form asks for a handle, explains that it is what others
 * see, and confirms that the email address is used only for sign-in and account recovery. Client
 * validation runs first for immediate feedback; the server enforces the same rules authoritatively.
 */
export function RegisterPage() {
  const navigate = useNavigate();
  const register = useSessionStore((state) => state.register);
  const [form, setForm] = useState({ email: '', username: '', displayName: '', password: '' });
  const [touched, setTouched] = useState({});
  const [serverError, setServerError] = useState(null);
  const [busy, setBusy] = useState(false);

  useDocumentTitle('Create account');

  const errors = {
    email: validateEmail(form.email),
    username: validateUsername(form.username),
    displayName: validateDisplayName(form.displayName),
    password: validatePassword(form.password),
  };
  const hasErrors = Object.values(errors).some(Boolean);

  function update(field) {
    return (event) => setForm((current) => ({ ...current, [field]: event.target.value }));
  }

  function blur(field) {
    return () => setTouched((current) => ({ ...current, [field]: true }));
  }

  async function submit(event) {
    event.preventDefault();
    setTouched({ email: true, username: true, displayName: true, password: true });
    if (hasErrors) return;

    setBusy(true);
    setServerError(null);
    try {
      await register({
        email: form.email.trim().toLowerCase(),
        username: form.username.trim().replace(/^@/, '').toLowerCase(),
        displayName: form.displayName.trim(),
        password: form.password,
      });
      // A fresh account has a profile to complete (bio, avatar) — send them straight there.
      navigate('/onboarding', { replace: true });
    } catch (error) {
      setServerError(error);
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
        <h1>Create your pseudonymous account</h1>
        <p className="auth-card__subtitle">
          Pick a username — it is the only thing other people see. No real name, no phone number.
        </p>

        {serverError ? (
          <p className="alert alert--error" role="alert">
            {serverError.message}
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
              value={form.email}
              onChange={update('email')}
              onBlur={blur('email')}
              aria-invalid={Boolean(touched.email && errors.email)}
              aria-describedby="email-hint"
            />
            <span className="field__hint" id="email-hint">
              Used for sign-in and password recovery only. Never shown to other users.
            </span>
            {touched.email && errors.email ? (
              <span className="field__error">
                <Icon name="alert" size={14} /> {errors.email}
              </span>
            ) : null}
          </div>

          <div className="field">
            <label className="field__label" htmlFor="username">
              Username
            </label>
            <input
              id="username"
              required
              autoComplete="off"
              value={form.username}
              onChange={update('username')}
              onBlur={blur('username')}
              aria-invalid={Boolean(touched.username && errors.username)}
              aria-describedby="username-hint"
            />
            <span className="field__hint" id="username-hint">
              Lowercase letters, numbers and underscores. This becomes your @handle.
            </span>
            {touched.username && errors.username ? (
              <span className="field__error">
                <Icon name="alert" size={14} /> {errors.username}
              </span>
            ) : null}
          </div>

          <div className="field">
            <label className="field__label" htmlFor="displayName">
              Display name
            </label>
            <input
              id="displayName"
              required
              autoComplete="nickname"
              value={form.displayName}
              onChange={update('displayName')}
              onBlur={blur('displayName')}
              aria-invalid={Boolean(touched.displayName && errors.displayName)}
              aria-describedby="display-name-hint"
            />
            <span className="field__hint" id="display-name-hint">
              What people see next to your messages. A nickname is fine.
            </span>
            {touched.displayName && errors.displayName ? (
              <span className="field__error">
                <Icon name="alert" size={14} /> {errors.displayName}
              </span>
            ) : null}
          </div>

          <div className="field">
            <label className="field__label" htmlFor="password">
              Password
            </label>
            <input
              id="password"
              type="password"
              autoComplete="new-password"
              required
              value={form.password}
              onChange={update('password')}
              onBlur={blur('password')}
              aria-invalid={Boolean(touched.password && errors.password)}
              aria-describedby="password-hint"
            />
            <span className="field__hint" id="password-hint">
              At least 12 characters. A passphrase of a few unrelated words is both stronger and easier to
              remember than something like “P@ssw0rd!”.
            </span>
            {touched.password && errors.password ? (
              <span className="field__error">
                <Icon name="alert" size={14} /> {errors.password}
              </span>
            ) : null}
          </div>

          <button type="submit" className="btn btn--block" disabled={busy}>
            {busy ? 'Creating account…' : 'Create account'}
          </button>
        </form>

        <p className="auth-card__footer">
          Already have an account? <Link to="/login">Sign in</Link>
        </p>
      </div>
    </main>
  );
}

export default RegisterPage;
