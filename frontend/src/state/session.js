import { create } from 'zustand';
import { authApi } from '../api/auth.js';

/**
 * Who is signed in.
 *
 * The store deliberately holds no credentials: the server keeps the session and the browser holds an
 * HttpOnly cookie, so "am I signed in?" is answered by asking the server once at start-up and then by
 * reacting to 401s. `status` drives the route guard, which avoids the classic flash of a login screen
 * for a user who is in fact authenticated.
 */
export const SessionStatus = Object.freeze({
  UNKNOWN: 'UNKNOWN',
  AUTHENTICATED: 'AUTHENTICATED',
  ANONYMOUS: 'ANONYMOUS',
});

export const useSessionStore = create((set, get) => ({
  status: SessionStatus.UNKNOWN,
  user: null,
  error: null,

  async bootstrap() {
    try {
      const profile = await authApi.me();
      set({ status: SessionStatus.AUTHENTICATED, user: profile, error: null });
    } catch (error) {
      if (error.status === 401) {
        // A definite answer: there is no session. Anything else leaves us genuinely unsure.
        set({ status: SessionStatus.ANONYMOUS, user: null, error: null });
        return;
      }
      // Unreachable server, timeout or 5xx: we do not know whether there is a session, so we stay
      // UNKNOWN and keep the error. The route guards render an explanation with a retry — pretending
      // the user is signed out would send them to a login form that cannot possibly work either.
      set({ status: SessionStatus.UNKNOWN, user: null, error });
    }
  },

  async login(credentials) {
    const user = await authApi.login(credentials);
    set({ status: SessionStatus.AUTHENTICATED, user, error: null });
    return user;
  },

  async register(payload) {
    const user = await authApi.register(payload);
    set({ status: SessionStatus.AUTHENTICATED, user, error: null });
    return user;
  },

  async logout() {
    try {
      await authApi.logout();
    } finally {
      set({ status: SessionStatus.ANONYMOUS, user: null });
    }
  },

  async logoutEverywhere() {
    try {
      await authApi.logoutAll();
    } finally {
      set({ status: SessionStatus.ANONYMOUS, user: null });
    }
  },

  /** Re-runs the start-up session check (used by the "Try again" action). */
  async retry() {
    set({ status: SessionStatus.UNKNOWN, error: null });
    await get().bootstrap();
  },

  /** Dismisses a stale bootstrap error once the server is reachable again. */
  clearError() {
    set({ error: null });
  },

  /** Applied when any API call or the socket reports that the session is gone. */
  markSignedOut() {
    if (get().status !== SessionStatus.ANONYMOUS) {
      set({ status: SessionStatus.ANONYMOUS, user: null });
    }
  },

  patchUser(patch) {
    set((state) => (state.user ? { user: { ...state.user, ...patch } } : {}));
  },

  reset() {
    set({ status: SessionStatus.UNKNOWN, user: null, error: null });
  },
}));
