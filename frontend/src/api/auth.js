import { http } from './client.js';
import { endpoints } from './endpoints.js';

/**
 * Authentication calls.
 *
 * There is no token handling here by design: the server sets an HttpOnly session cookie, and the only
 * thing the client can and must do is send the CSRF header (handled centrally in client.js).
 */
export const authApi = {
  /** Register and sign in. The response envelope carries `{user, session}`; callers want the account. */
  register: async (payload) => (await http.post(endpoints.register(), payload)).user,
  /** `identifier` is an email address or a username — the server accepts either. */
  login: async (payload) => (await http.post(endpoints.login(), payload)).user,
  logout: () => http.post(endpoints.logout()),
  logoutAll: () => http.post(endpoints.logoutAll()),
  /**
   * The start-up session check.
   *
   * Given a tighter deadline than ordinary requests: this call gates the whole application, and if the
   * backend is not running the user deserves to be told in seconds rather than two minutes.
   */
  me: (options) => http.get(endpoints.currentSession(), { timeout: 8000, ...options }),
  listSessions: () => http.get(endpoints.sessions()),
  revokeSession: (id) => http.delete(endpoints.session(id)),
  changePassword: (payload) => http.post(endpoints.changePassword(), payload),
  forgotPassword: (payload) => http.post(endpoints.forgotPassword(), payload),
  resetPassword: (payload) => http.post(endpoints.resetPassword(), payload),
};
