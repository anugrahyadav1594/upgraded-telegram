import { useEffect } from 'react';
import { Link } from 'react-router-dom';

import { EmptyState } from '../components/EmptyState.jsx';
import { LoadingBlock } from '../components/Spinner.jsx';
import { Icon } from '../components/Icon.jsx';
import { useNotificationList, useNotificationMutations, useNotificationSettings } from '../hooks/useNotifications.js';
import { useDocumentTitle } from '../hooks/useDocumentTitle.js';
import { relativeTime } from '../utils/time.js';

const TYPE_LABEL = {
  NEW_MESSAGE: 'New message',
  MENTION: 'Mention',
  CONVERSATION_UPDATE: 'Conversation update',
  MODERATION_UPDATE: 'Moderation update',
  ACCOUNT: 'Account',
};

/**
 * Notification centre.
 *
 * Read state is explicit: opening the page marks what the user has seen, and the settings below control
 * what is created in the first place — muting a conversation means the server writes no row at all,
 * rather than writing one and hiding it here.
 */
export function NotificationsPage() {
  const list = useNotificationList(false);
  const { markRead, remove, updateSettings } = useNotificationMutations();
  const settings = useNotificationSettings();

  useDocumentTitle('Notifications');

  const items = list.data?.pages?.flatMap((page) => page.items || []) || [];
  const hasUnread = items.some((item) => !item.read);

  useEffect(() => {
    if (hasUnread) markRead.mutate(undefined);
    // Marking read once on entry is the intended behaviour; re-running on every render would fight the user.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hasUnread]);

  return (
    <main className="page">
      <div className="page__header">
        <h1 className="page__title">Notifications</h1>
        <Link className="btn btn--ghost" to="/">
          Back to chats
        </Link>
      </div>

      <section className="section">
        {list.isLoading ? (
          <LoadingBlock label="Loading notifications…" />
        ) : items.length === 0 ? (
          <EmptyState
            icon="bell"
            title="Nothing new"
            description="Messages, mentions and moderation decisions will appear here."
          />
        ) : (
          <ul className="list">
            {items.map((item) => (
              <li
                key={item.id}
                className={`notification-item${item.read ? '' : ' notification-item--unread'}`}
              >
                <div style={{ flex: 1 }}>
                  <div className="row row--between">
                    <strong>{TYPE_LABEL[item.type] || item.type}</strong>
                    <span className="text-xs muted">{relativeTime(item.createdAt)}</span>
                  </div>
                  <div className="text-sm">{item.preview || 'You have new activity.'}</div>
                  {item.actor ? (
                    <div className="text-xs muted">from {item.actor.displayName}</div>
                  ) : null}
                  <div className="row" style={{ marginTop: 8 }}>
                    {item.conversationId ? (
                      <Link className="btn btn--secondary text-xs" to={`/c/${item.conversationId}`}>
                        Open conversation
                      </Link>
                    ) : null}
                    <button
                      type="button"
                      className="btn btn--ghost text-xs"
                      onClick={() => remove.mutate(item.id)}
                      aria-label="Delete notification"
                    >
                      <Icon name="close" size={14} /> Dismiss
                    </button>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="section">
        <h2 className="section__title">What you get notified about</h2>
        <p className="section__description">
          Turning something off stops the notification from being created at all — it is not just hidden.
        </p>
        {settings.isLoading ? (
          <LoadingBlock label="Loading preferences…" />
        ) : (
          <div className="card">
            {[
              ['newMessage', 'New messages', 'Direct messages from people you have not muted.'],
              ['mentions', 'Mentions', 'When somebody mentions you by username.'],
              ['conversationUpdates', 'Conversation updates', 'New conversations and conversation changes.'],
              ['moderationUpdates', 'Moderation decisions', 'Outcomes of reports you submitted.'],
              ['emailDigest', 'Email digest', 'A summary by email. Off by default; nothing is sent while the mailer is disabled.'],
            ].map(([key, label, hint]) => (
              <label className="checkbox" key={key}>
                <input
                  type="checkbox"
                  checked={Boolean(settings.data?.[key])}
                  disabled={updateSettings.isPending}
                  onChange={(event) => updateSettings.mutate({ [key]: event.target.checked })}
                />
                <span className="checkbox__text">
                  {label}
                  <span className="checkbox__hint">{hint}</span>
                </span>
              </label>
            ))}
          </div>
        )}
      </section>
    </main>
  );
}

export default NotificationsPage;
