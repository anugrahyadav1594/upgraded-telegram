import { http } from './client.js';
import { endpoints } from './endpoints.js';

/**
 * Profile, privacy and block calls.
 *
 * `getProfile` returns `null` for a 404 instead of throwing: "this username does not exist" is a
 * normal navigation outcome (shared link, renamed account), not an error state to surface as a failure.
 */
export const usersApi = {
  async getProfile(username) {
    try {
      return await http.get(endpoints.userProfile(username));
    } catch (error) {
      if (error.status === 404) return null;
      throw error;
    }
  },
  search: (q, cursor, limit = 20) => http.get(endpoints.searchUsers(), { params: { q, cursor, limit } }),
  /**
   * Presence is a separate call from the profile on purpose: online status and last-seen are privacy
   * decisions the user can change independently of who may see their profile.
   */
  async presence(userId) {
    try {
      return await http.get(endpoints.presence(userId));
    } catch (error) {
      // A blocked/hidden user returns 404 by design; absent presence is not an error to surface.
      if (error.status === 404 || error.status === 403) return null;
      throw error;
    }
  },
  updateMe: (payload) => http.patch(endpoints.myProfile(), payload),
  /** Irreversible: erases personal data and anonymises authored messages. */
  deleteAccount: () => http.delete(endpoints.myProfile()),
  getPrivacy: () => http.get(endpoints.privacy()),
  updatePrivacy: (payload) => http.patch(endpoints.privacy(), payload),
  listBlocks: () => http.get(endpoints.blocks()),
  block: (userId) => http.post(endpoints.block(userId)),
  unblock: (userId) => http.delete(endpoints.block(userId)),
};
