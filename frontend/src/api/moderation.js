import { http } from './client.js';
import { endpoints } from './endpoints.js';

/**
 * Reports.
 *
 * The reported user is never told, and the reporter is never given a target's history — the response is
 * the reporter's own submission only.
 */
export const moderationApi = {
  /** Exactly one of `targetUserId` / `targetMessageId` is set, matching `targetType`. */
  report: ({ targetType, targetUserId, targetMessageId, reason, description }) =>
    http.post(endpoints.reports(), { targetType, targetUserId, targetMessageId, reason, description }),
  mine: (limit = 20) => http.get('/api/v1/reports/mine', { params: { limit } }),
};

export const REPORT_REASONS = [
  { value: 'SPAM', label: 'Spam or unwanted advertising' },
  { value: 'HARASSMENT', label: 'Harassment or bullying' },
  { value: 'HATE_SPEECH', label: 'Hate speech' },
  { value: 'IMPERSONATION', label: 'Impersonation' },
  { value: 'SEXUAL_CONTENT', label: 'Sexual content' },
  { value: 'VIOLENCE', label: 'Violence or threats' },
  { value: 'SELF_HARM', label: 'Self-harm' },
  { value: 'OTHER', label: 'Something else' },
];
