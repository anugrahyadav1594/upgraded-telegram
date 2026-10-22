import { useEffect } from 'react';

import { usersApi } from '../api/users.js';
import { useRealtimeStore } from '../state/realtime.js';

/**
 * Presence for a set of users.
 *
 * The socket pushes updates, but a page that loads after a user came online still needs the current
 * value: presence is read once over REST for the ids on screen, and thereafter maintained from frames.
 * A user who hides their status simply has no entry — the UI renders nothing rather than inventing
 * "offline", because claiming somebody is offline is itself a disclosure.
 */
export function usePresence(userIds = []) {
  const setPresenceBatch = useRealtimeStore((state) => state.setPresenceBatch);

  const key = userIds.filter(Boolean).sort().join(',');

  useEffect(() => {
    if (!key) return undefined;
    const ids = key.split(',');
    let cancelled = false;
    // Presence arrives with the profile payload for chat surfaces; the batch refresh here covers lists
    // whose data came from a cached page (for example going back to a conversation after a reload).
    Promise.all(
      ids.map((id) =>
        usersApi
          .presence(id)
          .then((presence) => (presence ? { userId: id, ...presence } : null))
          .catch(() => null),
      ),
    ).then((entries) => {
      if (!cancelled) setPresenceBatch(entries.filter(Boolean));
    });
    return () => {
      cancelled = true;
    };
  }, [key, setPresenceBatch]);
}
