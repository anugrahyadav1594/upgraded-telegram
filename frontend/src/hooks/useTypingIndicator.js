import { useCallback, useEffect, useRef } from 'react';

import { chatCommands } from '../websocket/commands.js';
import { useRealtimeStore } from '../state/realtime.js';
import { useSessionStore } from '../state/session.js';

const TYPING_STOP_AFTER_MS = 3000;

/**
 * Typing notifications.
 *
 * The indicator is ephemeral by design: it lives in Redis with a short TTL and is never written to
 * PostgreSQL. The client mirrors that contract — it sends a start once, keeps the server's TTL alive
 * while the user types, and always sends a stop on send/blur, so nobody is left "typing" forever.
 */
export function useTypingPublisher(conversationId) {
  const timerRef = useRef(null);
  const activeRef = useRef(false);
  const userId = useSessionStore((state) => state.user?.id);

  const publish = useCallback(
    (isTyping) => {
      if (!conversationId || !userId) return;
      chatCommands.typing({ conversationId, isTyping });
    },
    [conversationId, userId],
  );

  const notifyKeystroke = useCallback(() => {
    if (!activeRef.current) {
      activeRef.current = true;
      publish(true);
    }
    window.clearTimeout(timerRef.current);
    timerRef.current = window.setTimeout(() => {
      activeRef.current = false;
      publish(false);
    }, TYPING_STOP_AFTER_MS);
  }, [publish]);

  const stopTyping = useCallback(() => {
    window.clearTimeout(timerRef.current);
    if (activeRef.current) {
      activeRef.current = false;
      publish(false);
    }
  }, [publish]);

  useEffect(() => () => {
    window.clearTimeout(timerRef.current);
    if (activeRef.current) publish(false);
  }, [publish]);

  return { notifyKeystroke, stopTyping };
}

/**
 * Reads the typing state for a conversation and returns display names, excluding the current user.
 *
 * The store prunes entries whose TTL has lapsed, so this is a plain derivation with no timers of its own.
 */
export function useTypingNames(conversationId) {
  const typing = useRealtimeStore((state) => (conversationId ? state.typing[conversationId] : undefined));
  const participants = useRealtimeStore((state) => state.presence);
  const userId = useSessionStore((state) => state.user?.id);

  if (!typing) return { names: [], text: '' };
  const names = Object.keys(typing)
    .filter((id) => id !== userId)
    .map((id) => participants[id]?.displayName || 'Someone');

  if (names.length === 0) return { names: [], text: '' };
  if (names.length === 1) return { names, text: `${names[0]} is typing…` };
  if (names.length === 2) return { names, text: `${names[0]} and ${names[1]} are typing…` };
  return { names, text: 'Several people are typing…' };
}
