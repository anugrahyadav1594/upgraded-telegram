import { useEffect, useRef } from 'react';

/**
 * Calls `onLoadMore` when a sentinel element scrolls into view.
 *
 * IntersectionObserver rather than scroll maths: it survives nested scroll containers, does not fire on
 * every pixel, and reports entry/exit rather than positions.
 */
export function useInfiniteScroll(onLoadMore, { enabled = true, rootMargin = '400px' } = {}) {
  const sentinelRef = useRef(null);
  const callbackRef = useRef(onLoadMore);

  // Keep the latest callback without re-creating the observer: observers are cheap to keep and
  // expensive to churn, and the caller's handler closes over fresh state every render.
  useEffect(() => {
    callbackRef.current = onLoadMore;
  });

  useEffect(() => {
    const node = sentinelRef.current;
    if (!node || !enabled || typeof IntersectionObserver === 'undefined') return undefined;

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) callbackRef.current?.();
        });
      },
      { rootMargin },
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, [enabled, rootMargin]);

  return sentinelRef;
}
