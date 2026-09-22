import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { usersApi } from '../api/users.js';
import { Icon } from '../components/Icon.jsx';
import { useDocumentTitle } from '../hooks/useDocumentTitle.js';
import { useNotificationStore } from '../state/notifications.js';
import { useSessionStore } from '../state/session.js';
import { validateDisplayName } from '../utils/validation.js';

/**
 * Finishing the profile.
 *
 * Only the display name is required — the product's premise is that you can be interesting without being
 * identified, so a bio and an avatar are offered and skippable. The screen also sets the privacy defaults
 * in words, so nobody discovers them by surprise later.
 */
export function OnboardingPage() {
  const navigate = useNavigate();
  const user = useSessionStore((state) => state.user);
  const patchUser = useSessionStore((state) => state.patchUser);
  const pushToast = useNotificationStore((state) => state.pushToast);

  const [displayName, setDisplayName] = useState(user?.displayName || '');
  const [bio, setBio] = useState(user?.bio || '');
  const [avatarUrl, setAvatarUrl] = useState(user?.avatarUrl || '');
  const [usernamePublic, setUsernamePublic] = useState(user?.usernamePublic ?? true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  useDocumentTitle('Set up your profile');

  const nameError = validateDisplayName(displayName);

  async function submit(event) {
    event.preventDefault();
    if (nameError) return;
    setBusy(true);
    setError(null);
    try {
      const updated = await usersApi.updateMe({
        displayName: displayName.trim(),
        bio: bio.trim(),
        avatarUrl: avatarUrl.trim() || undefined,
        usernamePublic,
      });
      patchUser({ ...updated, profileComplete: true });
      pushToast({ title: 'You are all set', body: 'Find someone by username to start chatting.', tone: 'info' });
      navigate('/', { replace: true });
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
        <h1>Set up your profile</h1>
        <p className="auth-card__subtitle">
          You are <strong>@{user?.username}</strong>. Everything here except the display name is optional,
          and you can change all of it later in settings.
        </p>

        {error ? (
          <p className="alert alert--error" role="alert">
            {error.message}
          </p>
        ) : null}

        <form onSubmit={submit} noValidate>
          <div className="field">
            <label className="field__label" htmlFor="display-name">
              Display name
            </label>
            <input
              id="display-name"
              value={displayName}
              maxLength={64}
              required
              autoFocus
              onChange={(event) => setDisplayName(event.target.value)}
              aria-invalid={Boolean(nameError)}
            />
            {nameError ? <span className="field__error">{nameError}</span> : null}
          </div>

          <div className="field">
            <label className="field__label" htmlFor="bio">
              Short bio (optional)
            </label>
            <textarea
              id="bio"
              rows={3}
              maxLength={280}
              value={bio}
              placeholder="What are you into?"
              onChange={(event) => setBio(event.target.value)}
            />
            <span className="field__hint">{bio.length}/280</span>
          </div>

          <div className="field">
            <label className="field__label" htmlFor="avatar">
              Avatar URL (optional)
            </label>
            <input
              id="avatar"
              type="url"
              placeholder="https://…"
              value={avatarUrl}
              onChange={(event) => setAvatarUrl(event.target.value)}
            />
          </div>

          <label className="checkbox">
            <input
              type="checkbox"
              checked={usernamePublic}
              onChange={(event) => setUsernamePublic(event.target.checked)}
            />
            <span className="checkbox__text">
              Let people find me by username
              <span className="checkbox__hint">
                Off means only people you message first can find you. You can change this at any time.
              </span>
            </span>
          </label>

          <button type="submit" className="btn btn--block" disabled={busy || Boolean(nameError)}>
            {busy ? 'Saving…' : 'Start chatting'}
            <Icon name="chat" size={16} />
          </button>
        </form>
      </div>
    </main>
  );
}

export default OnboardingPage;
