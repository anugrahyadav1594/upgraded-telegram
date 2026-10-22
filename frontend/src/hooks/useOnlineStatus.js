import { useEffect, useState } from 'react';

/**
 * Browser-level connectivity.
 *
 * This is deliberately separate from the WebSocket connection state: the OS can be online while the
 * socket is reconnecting, and a browser can be offline while a cached page still renders. The UI shows
 * both facts rather than collapsing them into one confusing "offline" label.
 */
export function useOnlineStatus() {
  const [online, setOnline] = useState(() => (typeof navigator === 'undefined' ? true : navigator.onLine));

  useEffect(() => {
    const goOnline = () => setOnline(true);
    const goOffline = () => setOnline(false);
    window.addEventListener('online', goOnline);
    window.addEventListener('offline', goOffline);
    return () => {
      window.removeEventListener('online', goOnline);
      window.removeEventListener('offline', goOffline);
    };
  }, []);

  return online;
}
