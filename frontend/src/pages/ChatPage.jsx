import { useCallback, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';

import { ChatHeader } from '../components/ChatHeader.jsx';
import { ConnectionBanner } from '../components/ConnectionBanner.jsx';
import { ConversationList } from '../components/ConversationList.jsx';
import { EmptyState } from '../components/EmptyState.jsx';
import { Icon } from '../components/Icon.jsx';
import { MessageComposer } from '../components/MessageComposer.jsx';
import { MessageList } from '../components/MessageList.jsx';
import { Modal } from '../components/Modal.jsx';
import { ReportDialog } from '../components/ReportDialog.jsx';
import { UserSearchPanel } from '../components/UserSearchPanel.jsx';
import { useConversation, useConversations, useConversationActions, useCreateConversation } from '../hooks/useConversations.js';
import { useKeyboardShortcuts } from '../hooks/useKeyboardShortcuts.js';
import { useIsMobile } from '../hooks/useMediaQuery.js';
import { useMarkConversationRead, useMessageActions, useMessageHistory, useRetryMessage, useSendMessage } from '../hooks/useMessages.js';
import { useDocumentTitle } from '../hooks/useDocumentTitle.js';
import { useSessionStore } from '../state/session.js';
import { useNotificationStore } from '../state/notifications.js';
import { focusElement } from '../utils/a11y.js';

/**
 * The chat surface: navigation rail, conversation list, and one thread.
 *
 * Three layouts share this component because they share the same data. Desktop shows list and thread
 * side by side; tablet drops the rail to icons; mobile shows one pane at a time and the URL decides
 * which — so the browser's back button behaves the way people expect on a phone.
 */
export function ChatPage() {
  const { conversationId } = useParams();
  const navigate = useNavigate();
  const isMobile = useIsMobile();
  const user = useSessionStore((state) => state.user);
  const pushToast = useNotificationStore((state) => state.pushToast);

  const [newChatOpen, setNewChatOpen] = useState(false);
  const [filter, setFilter] = useState('');

  /**
   * Transient thread UI (open dialog, message being edited, report target).
   *
   * Tagged with the conversation it belongs to and derived on render, so switching threads starts from a
   * clean slate without an effect that resets five pieces of state and re-renders twice.
   */
  const [threadUi, setThreadUi] = useState({
    conversationId,
    detailsOpen: false,
    editingMessageId: null,
    reportTarget: null,
  });
  const ui =
    threadUi.conversationId === conversationId
      ? threadUi
      : { conversationId, detailsOpen: false, editingMessageId: null, reportTarget: null };
  const patchUi = (patch) => setThreadUi({ ...ui, ...patch });

  const conversationsQuery = useConversations();
  const conversationDetail = useConversation(conversationId);
  const createConversation = useCreateConversation();
  const searchInputId = 'conversation-filter';

  const conversations = useMemo(
    () => conversationsQuery.data?.pages?.flatMap((page) => page.items || []) || [],
    [conversationsQuery.data],
  );

  const conversation = useMemo(
    () => conversations.find((item) => item.id === conversationId) || conversationDetail.data || null,
    [conversations, conversationDetail.data, conversationId],
  );

  const filtered = useMemo(() => {
    const term = filter.trim().toLowerCase();
    if (!term) return conversations;
    return conversations.filter((item) =>
      `${item.peer?.displayName || ''} ${item.peer?.username || ''}`.toLowerCase().includes(term),
    );
  }, [conversations, filter]);

  const history = useMessageHistory(conversationId, { enabled: Boolean(conversation) });
  const sendMessage = useSendMessage(conversationId);
  const retryMessage = useRetryMessage(conversationId);
  const markRead = useMarkConversationRead(conversationId);
  const { edit, remove, toggleReaction } = useMessageActions();
  const { setMuted, remove: removeConversation } = useConversationActions(conversationId);

  useDocumentTitle(conversation?.peer?.displayName);

  const selectConversation = useCallback(
    (next) => {
      navigate(`/c/${next.id}`);
      setFilter('');
    },
    [navigate],
  );

  const focusSearch = useCallback(() => {
    focusElement(document.getElementById(searchInputId));
  }, []);

  useKeyboardShortcuts({
    onSearch: focusSearch,
    onEscape: () => {
      setNewChatOpen(false);
      patchUi({ detailsOpen: false, editingMessageId: null, reportTarget: null });
    },
  });

  async function startConversation(profile) {
    try {
      const { conversation: created, created: isNew } = await createConversation.mutateAsync(profile.id);
      setNewChatOpen(false);
      navigate(`/c/${created.id}`);
      pushToast({
        title: isNew ? 'Conversation started' : 'Conversation opened',
        body: `You are chatting with ${profile.displayName}.`,
        tone: 'info',
      });
    } catch (error) {
      pushToast({ title: 'Could not start that conversation', body: error.message, tone: 'warning' });
    }
  }

  function handleSend(content) {
    sendMessage.mutate(
      { content },
      {
        onError: (error) =>
          pushToast({ title: 'Message not sent', body: error.message, tone: 'warning' }),
      },
    );
  }

  const threadOpen = Boolean(conversationId && conversation);
  const shellClass = [
    'app-shell__main',
    isMobile && threadOpen ? 'app-shell--thread-open' : '',
    isMobile && !threadOpen ? 'app-shell--list-open' : '',
  ]
    .filter(Boolean)
    .join(' ');

  const canMessage = conversation?.peer ? conversation.peer.canMessage !== false : false;

  return (
    <div className={shellClass}>
      <section className="conversation-pane" aria-label="Conversations">
        <div className="conversation-pane__header">
          <h1 className="conversation-pane__title">Chats</h1>
          <button
            type="button"
            className="icon-btn"
            aria-label="Start a new conversation"
            title="New conversation"
            onClick={() => setNewChatOpen(true)}
          >
            <Icon name="plus" size={20} />
          </button>
        </div>

        <div className="conversation-search">
          <label className="sr-only" htmlFor={searchInputId}>
            Filter conversations
          </label>
          <input
            id={searchInputId}
            type="search"
            placeholder="Filter by name…  (press /)"
            value={filter}
            onChange={(event) => setFilter(event.target.value)}
          />
        </div>

        <ConversationList
          conversations={filtered}
          isLoading={conversationsQuery.isLoading}
          isError={conversationsQuery.isError}
          error={conversationsQuery.error}
          hasNextPage={conversationsQuery.hasNextPage}
          isFetchingNextPage={conversationsQuery.isFetchingNextPage}
          fetchNextPage={conversationsQuery.fetchNextPage}
          onSelect={selectConversation}
          selectedId={conversationId}
          currentUserId={user?.id}
        />
      </section>

      <section className="thread" aria-label="Conversation">
        <ConnectionBanner />

        {!conversation ? (
          <EmptyState
            icon="chat"
            title="Pick a conversation"
            description="Choose a chat on the left, or start a new one with the + button."
            action={
              <button type="button" className="btn" onClick={() => setNewChatOpen(true)}>
                <Icon name="plus" size={16} /> New conversation
              </button>
            }
          />
        ) : (
          <>
            <ChatHeader
              peer={conversation.peer}
              conversationId={conversation.id}
              muted={conversation.muted}
              onBack={() => navigate('/')}
              onOpenProfile={() => navigate(`/u/${conversation.peer?.username}`)}
              onToggleMute={() => setMuted.mutate(!conversation.muted)}
              onToggleDetails={() => patchUi({ detailsOpen: true })}
            />

            <MessageList
              conversationId={conversation.id}
              messages={history.messages}
              isLoading={history.isLoading}
              isError={history.isError}
              error={history.error}
              hasNextPage={history.hasNextPage}
              isFetchingNextPage={history.isFetchingNextPage}
              fetchNextPage={history.fetchNextPage}
              currentUserId={user?.id}
              peer={conversation.peer}
              editingMessageId={ui.editingMessageId}
              onRead={(messageId) => markRead(messageId)}
              onRetry={(clientMessageId) => retryMessage(clientMessageId)}
              onEdit={(payload) => {
                if (!payload) {
                  patchUi({ editingMessageId: null });
                  return;
                }
                if (payload.start) {
                  patchUi({ editingMessageId: payload.messageId });
                  return;
                }
                patchUi({ editingMessageId: null });
                edit.mutate(payload);
              }}
              onDelete={({ messageId }) => remove.mutate({ messageId, conversationId: conversation.id })}
              onReact={(payload) => toggleReaction.mutate(payload)}
              onReport={({ messageId }) =>
                patchUi({
                  reportTarget: {
                    targetType: 'MESSAGE',
                    targetMessageId: messageId,
                    label: `${conversation.peer?.displayName || 'this message'}'s message`,
                  },
                })
              }
            />

            <MessageComposer
              conversationId={conversation.id}
              onSend={handleSend}
              disabled={!canMessage}
              disabledReason={
                conversation.peer?.blockedByMe
                  ? 'You blocked this person. Unblock them from your profile settings to chat.'
                  : 'This person’s privacy settings do not allow new messages right now.'
              }
            />
          </>
        )}
      </section>

      <Modal open={newChatOpen} title="New conversation" onClose={() => setNewChatOpen(false)}>
        <UserSearchPanel onStartConversation={startConversation} busy={createConversation.isPending} />
      </Modal>

      <Modal
        open={ui.detailsOpen && Boolean(conversation)}
        title="Conversation details"
        onClose={() => patchUi({ detailsOpen: false })}
        footer={
          <button
            type="button"
            className="btn btn--danger"
            onClick={() => {
              removeConversation.mutate(undefined, { onSuccess: () => navigate('/') });
            }}
          >
            Delete conversation
          </button>
        }
      >
        {conversation ? (
          <div className="stack">
            <p className="text-sm muted">
              Deleting removes this conversation from your list. The other person keeps their copy, and
              your messages stay visible to them — chat is not unsendable history.
            </p>
            <label className="checkbox">
              <input
                type="checkbox"
                checked={Boolean(conversation.muted)}
                onChange={(event) => setMuted.mutate(event.target.checked)}
              />
              <span className="checkbox__text">
                Mute notifications
                <span className="checkbox__hint">You will still see messages, just without a badge.</span>
              </span>
            </label>
            <button
              type="button"
              className="btn btn--secondary"
              onClick={() =>
                patchUi({
                  reportTarget: {
                    targetType: 'USER',
                    targetUserId: conversation.peer?.id,
                    label: conversation.peer?.displayName || 'this user',
                  },
                })
              }
            >
              <Icon name="flag" size={16} /> Report {conversation.peer?.displayName}
            </button>
          </div>
        ) : null}
      </Modal>

      <ReportDialog
        open={Boolean(ui.reportTarget)}
        onClose={() => patchUi({ reportTarget: null })}
        targetType={ui.reportTarget?.targetType}
        targetUserId={ui.reportTarget?.targetUserId}
        targetMessageId={ui.reportTarget?.targetMessageId}
        targetLabel={ui.reportTarget?.label}
      />
    </div>
  );
}

export default ChatPage;
