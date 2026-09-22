import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import { Avatar } from '../components/Avatar.jsx';
import { EmptyState } from '../components/EmptyState.jsx';
import { Icon } from '../components/Icon.jsx';
import { LoadingBlock } from '../components/Spinner.jsx';
import { ReportDialog } from '../components/ReportDialog.jsx';
import { usersApi } from '../api/users.js';
import { useConversations, useCreateConversation } from '../hooks/useConversations.js';
import { useDocumentTitle } from '../hooks/useDocumentTitle.js';
import { usePresence } from '../hooks/usePresence.js';
import { useRealtimeStore } from '../state/realtime.js';
import { useSessionStore } from '../state/session.js';
import { useNotificationStore } from '../state/notifications.js';
import { fullTimestamp, relativeTime } from '../utils/time.js';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { queryKeys } from '../hooks/queryKeys.js';

/**
 * Public profile.
 *
 * Absence is handled honestly: a profile hidden by its owner, or invisible because of a block, is a 404
 * and this page says so without revealing which of the two it was — that distinction is exactly what the
 * privacy setting exists to hide.
 */
export function ProfilePage() {
  const { username } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const currentUser = useSessionStore((state) => state.user);
  const presenceMap = useRealtimeStore((state) => state.presence);
  const pushToast = useNotificationStore((state) => state.pushToast);
  const createConversation = useCreateConversation();
  const conversationsQuery = useConversations();
  const [reportOpen, setReportOpen] = useState(false);
  const [busy, setBusy] = useState(false);

  const profileQuery = useQuery({
    queryKey: queryKeys.profiles.detail(username),
    queryFn: () => usersApi.getProfile(username),
    enabled: Boolean(username),
  });

  const profile = profileQuery.data;
  usePresence(profile?.id ? [profile.id] : []);
  useDocumentTitle(profile ? profile.displayName : 'Profile');

  const isSelf = currentUser && profile && currentUser.username === profile.username;
  const presence = presenceMap[profile?.id];
  const online = presence?.status === 'ONLINE' || profile?.online;

  const existingConversationId =
    profile?.existingConversationId ||
    conversationsQuery.data?.pages
      ?.flatMap((page) => page.items || [])
      .find((conversation) => conversation.peer?.id === profile?.id)?.id;

  async function startConversation() {
    if (!profile) return;
    setBusy(true);
    try {
      const { conversation } = await createConversation.mutateAsync(profile.id);
      navigate(`/c/${conversation.id}`);
    } catch (error) {
      pushToast({ title: 'Could not open a conversation', body: error.message, tone: 'warning' });
    } finally {
      setBusy(false);
    }
  }

  async function toggleBlock() {
    if (!profile) return;
    setBusy(true);
    try {
      if (profile.blockedByMe) await usersApi.unblock(profile.id);
      else await usersApi.block(profile.id);
      await queryClient.invalidateQueries({ queryKey: queryKeys.profiles.detail(username) });
      await queryClient.invalidateQueries({ queryKey: queryKeys.conversations.all });
      pushToast({
        title: profile.blockedByMe ? 'Unblocked' : 'Blocked',
        body: profile.blockedByMe
          ? 'You can message each other again.'
          : 'They can no longer message you, and neither of you appears in the other’s search.',
        tone: 'info',
      });
    } catch (error) {
      pushToast({ title: 'Could not update block', body: error.message, tone: 'warning' });
    } finally {
      setBusy(false);
    }
  }

  if (profileQuery.isLoading) return <LoadingBlock label="Loading profile…" />;

  if (profileQuery.isError || !profile) {
    return (
      <div className="page">
        <EmptyState
          icon="user"
          title="Profile unavailable"
          description="This account does not exist, or its owner keeps it out of search and profiles."
          action={
            <button type="button" className="btn btn--secondary" onClick={() => navigate('/')}>
              Back to your chats
            </button>
          }
        />
      </div>
    );
  }

  return (
    <main className="page">
      <div className="page__header">
        <button type="button" className="btn btn--ghost" onClick={() => navigate(-1)}>
          <Icon name="back" size={16} /> Back
        </button>
      </div>

      <div className="profile-header">
        <Avatar user={profile} size={72} presence={online ? 'ONLINE' : 'OFFLINE'} showPresence />
        <div>
          <div className="profile-header__name">{profile.displayName}</div>
          <div className="profile-header__handle">
            {profile.usernamePublic ? `@${profile.username}` : 'username hidden'} ·{' '}
            {online
              ? 'Online'
              : presence?.status === 'HIDDEN'
                ? 'Presence hidden'
                : presence?.lastSeenAt || profile.lastSeenAt
                  ? `Last seen ${relativeTime(presence?.lastSeenAt || profile.lastSeenAt)}`
                  : 'Offline'}
          </div>
          <div className="text-xs muted">Member since {fullTimestamp(profile.memberSince)}</div>
        </div>
      </div>

      {profile.bio ? <p className="bio">{profile.bio}</p> : <p className="muted">No bio yet.</p>}

      {isSelf ? (
        <div className="row">
          <button type="button" className="btn" onClick={() => navigate('/settings')}>
            Edit your profile
          </button>
        </div>
      ) : (
        <div className="row" style={{ flexWrap: 'wrap' }}>
          {existingConversationId ? (
            <button
              type="button"
              className="btn"
              onClick={() => navigate(`/c/${existingConversationId}`)}
            >
              <Icon name="chat" size={16} /> Open conversation
            </button>
          ) : (
            <button
              type="button"
              className="btn"
              disabled={busy || !profile.canMessage || profile.blockedByMe}
              onClick={startConversation}
            >
              <Icon name="chat" size={16} />
              {profile.canMessage && !profile.blockedByMe ? 'Message' : 'Cannot message'}
            </button>
          )}
          <button type="button" className="btn btn--secondary" disabled={busy} onClick={toggleBlock}>
            <Icon name="block" size={16} /> {profile.blockedByMe ? 'Unblock' : 'Block'}
          </button>
          <button type="button" className="btn btn--ghost" onClick={() => setReportOpen(true)}>
            <Icon name="flag" size={16} /> Report
          </button>
        </div>
      )}

      {!profile.canMessage && !profile.blockedByMe ? (
        <p className="alert alert--error" role="status" style={{ marginTop: 16 }}>
          This person accepts messages from a limited set of people only.
        </p>
      ) : null}

      <ReportDialog
        open={reportOpen}
        onClose={() => setReportOpen(false)}
        targetType="USER"
        targetUserId={profile.id}
        targetLabel={profile.displayName}
      />
    </main>
  );
}

export default ProfilePage;
