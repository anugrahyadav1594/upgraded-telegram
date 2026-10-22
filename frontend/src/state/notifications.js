import { create } from 'zustand';

/**
 * Notification badge and in-app toasts.
 *
 * The badge is a single number that must agree with the server at all times: it is set from REST on
 * load, incremented by `notification.new` frames, and reset when the user reads. Toasts are transient
 * and never block the composer.
 */
let toastId = 0;

export const useNotificationStore = create((set, get) => ({
  unreadCount: 0,
  items: [],
  toasts: [],

  setUnreadCount(count) {
    set({ unreadCount: Math.max(0, count || 0) });
  },

  incrementUnread() {
    set((store) => ({ unreadCount: store.unreadCount + 1 }));
  },

  receive(notification) {
    set((store) => ({
      unreadCount: store.unreadCount + 1,
      items: [notification, ...store.items].slice(0, 50),
    }));
    if (notification?.level !== 'silent') {
      get().pushToast({
        title: notification?.title || 'New activity',
        body: notification?.body || '',
        tone: 'info',
      });
    }
  },

  setItems(items) {
    set({ items: items || [] });
  },

  markRead() {
    set((store) => ({ items: store.items.map((item) => ({ ...item, read: true })), unreadCount: 0 }));
  },

  pushToast({ title, body, tone = 'info', sticky = false }) {
    const id = ++toastId;
    set((store) => ({ toasts: [...store.toasts, { id, title, body, tone, sticky }] }));
    if (!sticky) {
      window.setTimeout(() => get().dismissToast(id), 5000);
    }
    return id;
  },

  dismissToast(id) {
    set((store) => ({ toasts: store.toasts.filter((toast) => toast.id !== id) }));
  },

  reset() {
    set({ unreadCount: 0, items: [], toasts: [] });
  },
}));
