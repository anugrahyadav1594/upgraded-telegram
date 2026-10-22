import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { authApi } from '../api/auth.js';
import { usersApi } from '../api/users.js';
import { ConfirmDialog } from '../components/Modal.jsx';
import { LoadingBlock } from '../components/Spinner.jsx';
import { ThemeToggle } from '../components/ThemeToggle.jsx';
import { Icon } from '../components/Icon.jsx';
import { queryKeys } from '../hooks/queryKeys.js';
import { useDocumentTitle } from '../hooks/useDocumentTitle.js';
import { useNotificationStore } from '../state/notifications.js';
import { useSessionStore } from '../state/session.js';
import { fullTimestamp } from '../utils/time.js';
import { validateDisplayName, validatePassword } from '../utils/validation.js';

/**
 * Settings.
 *
 * Every switch here maps to a server-side rule, and the copy says which: privacy settings decide what
 * other people can see and whether they can start a conversation; they are not cosmetic preferences the
 * client applies locally. Destructive actions (delete account, revoke sessions) are confirmed and
 * explained in terms of their real consequences.
 */
export function SettingsPage() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const user = useSessionStore((state) => state.user);
  const patchUser = useSessionStore((state) => state.patchUser);
  const logoutEverywhere = useSessionStore((state) => state.logoutEverywhere);
  const pushToast = useNotificationStore((state) => state.pushToast);

  const [profileForm, setProfileForm] = useState(() => ({
    displayName: user?.displayName || '',
    bio: user?.bio || '',
    avatarUrl: user?.avatarUrl || '',
    username: user?.username || '',
    usernamePublic: user?.usernamePublic ?? true,
  }));
  const [passwordForm, setPasswordForm] = useState({ currentPassword: '', newPassword: '' });
  const [confirm, setConfirm] = useState({ deleteAccount: false, logoutAll: false });

  useDocumentTitle('Settings');

  const privacy = useQuery({ queryKey: queryKeys.privacy, queryFn: () => usersApi.getPrivacy() });
  const blocks = useQuery({ queryKey: queryKeys.blocks, queryFn: () => usersApi.listBlocks() });
  const sessions = useQuery({ queryKey: queryKeys.sessions, queryFn: () => authApi.listSessions() });

  const saveProfile = useMutation({
    mutationFn: (payload) => usersApi.updateMe(payload),
    onSuccess: (updated) => {
      patchUser(updated);
      pushToast({ title: 'Profile saved', body: 'Your changes are live.', tone: 'info' });
      queryClient.invalidateQueries({ queryKey: queryKeys.session });
    },
    onError: (error) => pushToast({ title: 'Could not save profile', body: error.message, tone: 'warning' }),
  });

  const savePrivacy = useMutation({
    mutationFn: (payload) => usersApi.updatePrivacy(payload),
    onSuccess: (settings) => queryClient.setQueryData(queryKeys.privacy, settings),
    onError: (error) => pushToast({ title: 'Could not save privacy', body: error.message, tone: 'warning' }),
  });

  const unblock = useMutation({
    mutationFn: (userId) => usersApi.unblock(userId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.blocks }),
  });

  const changePassword = useMutation({
    mutationFn: (payload) => authApi.changePassword(payload),
    onSuccess: () => {
      setPasswordForm({ currentPassword: '', newPassword: '' });
      pushToast({
        title: 'Password changed',
        body: 'Other sessions were signed out. Sign in again on those devices.',
        tone: 'info',
      });
    },
    onError: (error) => pushToast({ title: 'Could not change password', body: error.message, tone: 'warning' }),
  });

  const deleteAccount = useMutation({
    mutationFn: () => usersApi.deleteAccount(),
    onSuccess: () => {
      useSessionStore.getState().markSignedOut();
      navigate('/login', { replace: true });
    },
  });

  const displayNameError = validateDisplayName(profileForm.displayName);
  const passwordError = validatePassword(passwordForm.newPassword);

  return (
    <main className="page">
      <div className="page__header">
        <h1 className="page__title">Settings</h1>
        <Link className="btn btn--ghost" to="/">
          Back to chats
        </Link>
      </div>

      <section className="section">
        <h2 className="section__title">Appearance</h2>
        <p className="section__description">
          Your choice is stored in this browser only, along with nothing else. “System” follows your
          operating system’s light/dark setting, including while the tab stays open.
        </p>
        <div className="card row row--between">
          <span>Colour theme</span>
          <ThemeToggle />
        </div>
      </section>

      <section className="section">
        <h2 className="section__title">Profile</h2>
        <p className="section__description">
          Your username is your public handle. Changing it again is limited to once every seven days.
        </p>
        <form
          className="card"
          onSubmit={(event) => {
            event.preventDefault();
            if (displayNameError) return;
            saveProfile.mutate({
              displayName: profileForm.displayName.trim(),
              bio: profileForm.bio.trim(),
              avatarUrl: profileForm.avatarUrl.trim() || undefined,
              username: profileForm.username.trim().replace(/^@/, '').toLowerCase() || undefined,
              usernamePublic: profileForm.usernamePublic,
            });
          }}
        >
          <div className="field">
            <label className="field__label" htmlFor="display-name">
              Display name
            </label>
            <input
              id="display-name"
              value={profileForm.displayName}
              maxLength={64}
              onChange={(event) => setProfileForm({ ...profileForm, displayName: event.target.value })}
              aria-invalid={Boolean(displayNameError)}
            />
            {displayNameError ? <span className="field__error">{displayNameError}</span> : null}
          </div>
          <div className="field">
            <label className="field__label" htmlFor="username">
              Username
            </label>
            <input
              id="username"
              value={profileForm.username}
              maxLength={32}
              onChange={(event) => setProfileForm({ ...profileForm, username: event.target.value })}
            />
            <span className="field__hint">Lowercase letters, numbers and underscores.</span>
          </div>
          <div className="field">
            <label className="field__label" htmlFor="bio">
              Bio
            </label>
            <textarea
              id="bio"
              rows={3}
              maxLength={280}
              value={profileForm.bio}
              onChange={(event) => setProfileForm({ ...profileForm, bio: event.target.value })}
            />
            <span className="field__hint">{profileForm.bio.length}/280</span>
          </div>
          <div className="field">
            <label className="field__label" htmlFor="avatar">
              Avatar URL
            </label>
            <input
              id="avatar"
              type="url"
              placeholder="https://…"
              value={profileForm.avatarUrl}
              onChange={(event) => setProfileForm({ ...profileForm, avatarUrl: event.target.value })}
            />
            <span className="field__hint">
              Must be an https link. Uploads are not supported yet; a link keeps the deployment free of
              file storage and scanning.
            </span>
          </div>
          <label className="checkbox">
            <input
              type="checkbox"
              checked={profileForm.usernamePublic}
              onChange={(event) => setProfileForm({ ...profileForm, usernamePublic: event.target.checked })}
            />
            <span className="checkbox__text">
              Show my username on my profile
              <span className="checkbox__hint">
                When off, people you have not messaged cannot see your handle.
              </span>
            </span>
          </label>
          <button type="submit" className="btn" disabled={saveProfile.isPending || Boolean(displayNameError)}>
            {saveProfile.isPending ? 'Saving…' : 'Save profile'}
          </button>
        </form>
      </section>

      <section className="section">
        <h2 className="section__title">Privacy</h2>
        <p className="section__description">
          These are enforced by the server for every request, including WebSocket subscriptions.
        </p>
        {privacy.isLoading ? (
          <LoadingBlock label="Loading privacy settings…" />
        ) : (
          <div className="card">
            {[
              ['showOnlineStatus', 'Show when I am online'],
              ['showLastSeen', 'Show when I was last seen'],
              ['showProfileInSearch', 'Let people find me by username'],
              ['readReceipts', 'Send read receipts', 'When off, people do not see Read on their messages.'],
            ].map(([key, label, hint]) => (
              <label className="checkbox" key={key}>
                <input
                  type="checkbox"
                  checked={Boolean(privacy.data?.[key])}
                  disabled={savePrivacy.isPending}
                  onChange={(event) => savePrivacy.mutate({ [key]: event.target.checked })}
                />
                <span className="checkbox__text">
                  {label}
                  {hint ? <span className="checkbox__hint">{hint}</span> : null}
                </span>
              </label>
            ))}
            <div className="field">
              <label className="field__label" htmlFor="allow-messages">
                Who can message me
              </label>
              <select
                id="allow-messages"
                value={privacy.data?.allowMessagesFrom || 'EVERYONE'}
                onChange={(event) => savePrivacy.mutate({ allowMessagesFrom: event.target.value })}
              >
                <option value="EVERYONE">Everyone</option>
                <option value="CONTACTS">People I already have a conversation with</option>
              </select>
              <span className="field__hint">
                Note: hiding your online status also hides your last-seen time — otherwise the setting
                would leak the very thing it hides.
              </span>
            </div>
          </div>
        )}
      </section>

      <section className="section">
        <h2 className="section__title">Blocked accounts</h2>
        {blocks.isLoading ? (
          <LoadingBlock label="Loading blocked accounts…" />
        ) : (blocks.data || []).length === 0 ? (
          <p className="muted text-sm">You have not blocked anyone.</p>
        ) : (
          <ul className="list">
            {(blocks.data || []).map((blocked) => (
              <li className="list-item" key={blocked.userId}>
                <span>
                  <strong>{blocked.displayName}</strong>{' '}
                  <span className="muted text-xs">@{blocked.username}</span>
                </span>
                <button
                  type="button"
                  className="btn btn--secondary"
                  onClick={() => unblock.mutate(blocked.userId)}
                >
                  Unblock
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="section">
        <h2 className="section__title">Active sessions</h2>
        <p className="section__description">
          Sessions are managed by the server and stored as hashes; signing out here ends them immediately.
        </p>
        {sessions.isLoading ? (
          <LoadingBlock label="Loading sessions…" />
        ) : (
          <ul className="list">
            {(sessions.data || []).map((session) => (
              <li className="list-item" key={session.id}>
                <span>
                  <strong>{session.current ? 'This device' : session.userAgent || 'Unknown device'}</strong>
                  <br />
                  <span className="text-xs muted">
                    Signed in {fullTimestamp(session.createdAt)} · last used {fullTimestamp(session.lastUsedAt)}
                  </span>
                </span>
                {session.current ? (
                  <span className="badge badge--muted">Current</span>
                ) : (
                  <button
                    type="button"
                    className="btn btn--secondary"
                    onClick={() =>
                      authApi.revokeSession(session.id).then(() =>
                        queryClient.invalidateQueries({ queryKey: queryKeys.sessions }),
                      )
                    }
                  >
                    Sign out
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}
        <div className="row" style={{ marginTop: 12 }}>
          <button type="button" className="btn btn--secondary" onClick={() => setConfirm({ ...confirm, logoutAll: true })}>
            Sign out of all devices
          </button>
        </div>
      </section>

      <section className="section">
        <h2 className="section__title">Password</h2>
        <form
          className="card"
          onSubmit={(event) => {
            event.preventDefault();
            if (passwordError || !passwordForm.currentPassword) return;
            changePassword.mutate(passwordForm);
          }}
        >
          <div className="field">
            <label className="field__label" htmlFor="current-password">
              Current password
            </label>
            <input
              id="current-password"
              type="password"
              autoComplete="current-password"
              value={passwordForm.currentPassword}
              onChange={(event) => setPasswordForm({ ...passwordForm, currentPassword: event.target.value })}
            />
          </div>
          <div className="field">
            <label className="field__label" htmlFor="new-password">
              New password
            </label>
            <input
              id="new-password"
              type="password"
              autoComplete="new-password"
              value={passwordForm.newPassword}
              onChange={(event) => setPasswordForm({ ...passwordForm, newPassword: event.target.value })}
              aria-invalid={Boolean(passwordError && passwordForm.newPassword)}
            />
            {passwordForm.newPassword ? (
              <span className="field__hint">{passwordError || 'Looks good.'}</span>
            ) : null}
          </div>
          <button
            type="submit"
            className="btn"
            disabled={changePassword.isPending || Boolean(passwordError) || !passwordForm.currentPassword}
          >
            Change password
          </button>
        </form>
      </section>

      <section className="section">
        <h2 className="section__title">Delete account</h2>
        <p className="section__description">
          Your profile and personal data are erased and your messages are anonymised. This cannot be undone.
        </p>
        <button
          type="button"
          className="btn btn--danger"
          onClick={() => setConfirm({ ...confirm, deleteAccount: true })}
        >
          <Icon name="alert" size={16} /> Delete my account
        </button>
      </section>

      <ConfirmDialog
        open={confirm.logoutAll}
        title="Sign out of all devices?"
        body="Every session, including this one, will end. You will need to sign in again."
        confirmLabel="Sign out everywhere"
        onClose={() => setConfirm({ ...confirm, logoutAll: false })}
        onConfirm={async () => {
          await logoutEverywhere();
          navigate('/login', { replace: true });
        }}
      />

      <ConfirmDialog
        open={confirm.deleteAccount}
        title="Delete your account?"
        body="Your profile, settings and sessions are deleted and your messages are anonymised so conversations stay readable for the other people in them."
        confirmLabel="Delete permanently"
        tone="danger"
        busy={deleteAccount.isPending}
        onClose={() => setConfirm({ ...confirm, deleteAccount: false })}
        onConfirm={() => deleteAccount.mutate()}
      />
    </main>
  );
}

export default SettingsPage;
