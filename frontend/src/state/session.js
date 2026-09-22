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
        set({ status: SessionStatus.ANONYMOUS, user: null, error: null });
        return;
      }
      // Anything else — an unreachable server, a 5xx — is treated as "not signed in yet", with the
      // error kept so the sign-in screen can explain what happened. Staying in UNKNOWN forever would
      // leave the app on a spinner whose only escape is a reload.
      set({ status: SessionStatus.ANONYMOUS, user: null, error });
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
