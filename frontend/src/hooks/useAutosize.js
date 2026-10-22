import { useCallback, useLayoutEffect, useRef } from 'react';

/**
 * Grows a textarea with its content up to a maximum, then scrolls.
 *
 * Chat composers that stay one line tall hide what the user is typing; ones that grow without a cap
 * push the message list off screen. This does exactly the useful range and nothing else.
 */
export function useAutosize({ maxHeight = 160, minHeight = 44 } = {}) {
  const ref = useRef(null);

  const resize = useCallback(() => {
    const element = ref.current;
    if (!element) return;
    element.style.height = 'auto';
    const next = Math.min(Math.max(element.scrollHeight, minHeight), maxHeight);
    element.style.height = `${next}px`;
    element.style.overflowY = element.scrollHeight > maxHeight ? 'auto' : 'hidden';
  }, [maxHeight, minHeight]);

  useLayoutEffect(() => {
    resize();
  });

  return { ref, resize };
}
