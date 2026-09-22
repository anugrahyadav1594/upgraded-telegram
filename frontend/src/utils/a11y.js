/**
 * Accessibility helpers.
 *
 * Chat is a hostile environment for screen readers if announcements are not curated: a visually hidden
 * live region that repeats every message and every presence tick makes the app unusable. The rule used
 * here is to announce *outcomes* ("message sent", "connection lost") and let the message list itself
 * carry the content.
 */
export function prefersReducedMotion() {
  if (typeof window === 'undefined' || !window.matchMedia) return false;
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

/** Announces a short status to assistive technology through a polite live region. */
export function announce(message, politeness = 'polite') {
  if (typeof document === 'undefined' || !message) return;
  const id = politeness === 'assertive' ? 'sr-announcer-assertive' : 'sr-announcer';
  let region = document.getElementById(id);
  if (!region) {
    region = document.createElement('div');
    region.id = id;
    region.className = 'sr-only';
    region.setAttribute('role', 'status');
    region.setAttribute('aria-live', politeness);
    region.setAttribute('aria-atomic', 'true');
    document.body.appendChild(region);
  }
  // Clearing first guarantees a change event even when the same text is announced twice.
  region.textContent = '';
  window.setTimeout(() => {
    region.textContent = message;
  }, 50);
}

/** Moves focus to an element without scrolling the whole page (dialogs, route changes). */
export function focusElement(element) {
  if (!element || typeof element.focus !== 'function') return;
  element.focus({ preventScroll: true });
}
