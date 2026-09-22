import { useEffect } from 'react';

/**
 * Global keyboard shortcuts.
 *
 * Shortcuts are registered in one listener and ignore keystrokes that arrive while the user is typing
 * in a field or has a dialog open — a chat app that steals "Escape" or "/" from a textarea is worse
 * than one without shortcuts.
 */
export function useKeyboardShortcuts({ onSearch, onEscape, onNextConversation, onPreviousConversation }) {
  useEffect(() => {
    function isTypingTarget(target) {
      if (!target) return false;
      const tag = target.tagName?.toLowerCase();
      return tag === 'input' || tag === 'textarea' || tag === 'select' || target.isContentEditable;
    }

    function handleKeyDown(event) {
      const typing = isTypingTarget(event.target);

      // Escape always works: it closes dialogs and cancels replies even from inside a text field.
      if (event.key === 'Escape') {
        onEscape?.();
        return;
      }
      // Everything else is suppressed while the user is typing or using a modifier combination that
      // belongs to the browser or the OS.
      if (typing || event.metaKey || event.ctrlKey) return;

      if (event.key === '/') {
        event.preventDefault();
        onSearch?.();
        return;
      }
      if (event.altKey && event.key === 'ArrowDown') {
        event.preventDefault();
        onNextConversation?.();
        return;
      }
      if (event.altKey && event.key === 'ArrowUp') {
        event.preventDefault();
        onPreviousConversation?.();
      }
    }

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onSearch, onEscape, onNextConversation, onPreviousConversation]);
}
