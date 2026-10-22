import { useEffect } from 'react';

const BASE_TITLE = 'REALCHAT';

/**
 * Keeps the tab title meaningful — unread counts in the title are how a background tab tells the user
 * something happened without stealing focus with a notification permission prompt.
 */
export function useDocumentTitle(title) {
  useEffect(() => {
    document.title = title ? `${title} · ${BASE_TITLE}` : BASE_TITLE;
    return () => {
      document.title = BASE_TITLE;
    };
  }, [title]);
}
