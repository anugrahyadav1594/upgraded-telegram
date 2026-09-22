import { create } from 'zustand';

/**
 * Theme state.
 *
 * Only the theme lives in localStorage — it is the one piece of client state that is useful before the
 * user is authenticated and harmless if it leaks. Everything sensitive (session, messages) stays on the
 * server, which is why there is no "token" or "user" in this file.
 *
 * `system` is a real option, not an alias for whatever the OS says right now: the store keeps the
 * user's intent and resolves it against `prefers-color-scheme` live, so a user who leaves the tab open
 * across dusk still gets the right colours.
 */
export const THEME_KEY = 'realchat.theme';
export const THEMES = ['light', 'dark', 'system'];

function readStoredTheme() {
  try {
    const stored = localStorage.getItem(THEME_KEY);
    return THEMES.includes(stored) ? stored : 'system';
  } catch {
    return 'system';
  }
}

export function systemPrefersDark() {
  return typeof window !== 'undefined' && window.matchMedia
    ? window.matchMedia('(prefers-color-scheme: dark)').matches
    : false;
}

export function resolveTheme(theme) {
  if (theme === 'system') return systemPrefersDark() ? 'dark' : 'light';
  return theme;
}

function applyTheme(theme) {
  if (typeof document === 'undefined') return;
  document.documentElement.dataset.theme = resolveTheme(theme);
  document.documentElement.dataset.themePreference = theme;
}

export const useThemeStore = create((set, get) => ({
  theme: readStoredTheme(),

  setTheme(next) {
    if (!THEMES.includes(next)) return;
    try {
      localStorage.setItem(THEME_KEY, next);
    } catch {
      // Private browsing without storage: the theme still applies for this page view.
    }
    applyTheme(next);
    set({ theme: next });
  },

  /** Cycle light → dark → system, for the keyboard shortcut and the compact toggle. */
  cycleTheme() {
    const order = ['light', 'dark', 'system'];
    const current = get().theme;
    get().setTheme(order[(order.indexOf(current) + 1) % order.length]);
  },

  apply() {
    applyTheme(get().theme);
  },
}));

/** Keeps the DOM in sync when the OS theme changes while the user picked "system". */
export function watchSystemTheme() {
  if (typeof window === 'undefined' || !window.matchMedia) return () => {};
  const query = window.matchMedia('(prefers-color-scheme: dark)');
  const listener = () => {
    if (useThemeStore.getState().theme === 'system') applyTheme('system');
  };
  query.addEventListener('change', listener);
  return () => query.removeEventListener('change', listener);
}
