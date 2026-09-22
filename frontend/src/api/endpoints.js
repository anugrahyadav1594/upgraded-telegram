/**
 * Every backend route the frontend uses, in one place.
 *
 * Centralising them means the path shapes from docs/api-specification.md appear exactly once; a typo
 * becomes a single-line fix instead of a hunt through components.
 */
export const endpoints = {
  // --- authentication & session ---
  register: () => '/api/v1/auth/register',
  login: () => '/api/v1/auth/login',
  logout: () => '/api/v1/auth/logout',
  logoutAll: () => '/api/v1/auth/logout-all',
  currentSession: () => '/api/v1/auth/me',
  sessions: () => '/api/v1/auth/sessions',
  session: (id) => `/api/v1/auth/sessions/${id}`,
  changePassword: () => '/api/v1/auth/change-password',
  forgotPassword: () => '/api/v1/auth/forgot-password',
  resetPassword: () => '/api/v1/auth/reset-password',

  // --- users, profiles, privacy, blocks ---
  myProfile: () => '/api/v1/users/me',
  userProfile: (username) => `/api/v1/users/${encodeURIComponent(username)}`,
  searchUsers: () => '/api/v1/users/search',
  privacy: () => '/api/v1/privacy',
  blocks: () => '/api/v1/blocks',
  block: (userId) => `/api/v1/users/${userId}/block`,

  // --- presence ---
  presence: (userId) => `/api/v1/users/${userId}/presence`,

  // --- conversations ---
  conversations: () => '/api/v1/conversations',
  conversation: (id) => `/api/v1/conversations/${id}`,
  conversationRead: (id) => `/api/v1/conversations/${id}/read`,
  conversationTyping: (id) => `/api/v1/conversations/${id}/typing`,

  // --- messages ---
  messages: (conversationId) => `/api/v1/conversations/${conversationId}/messages`,
  messagesResync: (conversationId) => `/api/v1/conversations/${conversationId}/messages/resync`,
  message: (messageId) => `/api/v1/messages/${messageId}`,
  messageRead: (messageId) => `/api/v1/messages/${messageId}/read`,
  messageReaction: (messageId) => `/api/v1/messages/${messageId}/reaction`,

  // --- notifications ---
  notifications: () => '/api/v1/notifications',
  notificationsRead: () => '/api/v1/notifications/read',
  notification: (id) => `/api/v1/notifications/${id}`,
  notificationSettings: () => '/api/v1/notifications/settings',

  // --- moderation ---
  reports: () => '/api/v1/reports',

  // --- operations ---
  health: () => '/actuator/health',
};
