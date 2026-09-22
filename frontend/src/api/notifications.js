import { http } from './client.js';
import { endpoints } from './endpoints.js';

export const notificationsApi = {
  list: ({ cursor, limit = 20, unreadOnly = false } = {}) =>
    http.get(endpoints.notifications(), { params: { cursor, limit, unreadOnly } }),
  markRead: (ids) => http.post(endpoints.notificationsRead(), { ids }),
  markAllRead: () => http.post(endpoints.notificationsRead(), {}),
  remove: (id) => http.delete(endpoints.notification(id)),
  getSettings: () => http.get(endpoints.notificationSettings()),
  updateSettings: (payload) => http.patch(endpoints.notificationSettings(), payload),
};
