import { useEffect, useState } from 'react';

/**
 * Debounces a fast-changing value (search input, typing notification) so the app does not send one
 * request per keystroke — the search endpoint is rate limited at 60/min and a human types faster.
 */
export function useDebouncedValue(value, delay = 250) {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), Math.max(0, delay));
    return () => window.clearTimeout(timer);
  }, [value, delay]);

  return debounced;
}
