/**
 * Client-side validation mirroring the server rules.
 *
 * These checks exist to give immediate feedback in the form; they are *not* the security boundary. The
 * server re-validates everything (see docs/api-specification.md), so a modified client gains nothing —
 * it only sees a slower error.
 */
export const USERNAME_PATTERN = /^[a-z0-9](?:[a-z0-9_]{1,30})[a-z0-9]$/;
export const MAX_MESSAGE_LENGTH = 4000;
export const MIN_PASSWORD_LENGTH = 12;
export const MAX_PASSWORD_LENGTH = 128;
export const MAX_BIO_LENGTH = 280;

const RESERVED = new Set([
  'admin', 'administrator', 'moderator', 'support', 'help', 'security', 'system', 'root', 'official',
  'staff', 'team', 'billing', 'abuse', 'realtimechat', 'realchat', 'deleted_account', 'deleted',
  'anonymous', 'me', 'you', 'null', 'undefined',
]);

/**
 * Detects ASCII control characters.
 *
 * Done with a character scan rather than a regular expression containing literal control characters:
 * such patterns are a lint error for good reason (they are invisible in review and easy to corrupt).
 *
 * @param {string} value        text to inspect
 * @param {number[]} allowed    code points that are acceptable (for example \n in a message)
 */
export function hasControlCharacters(value, allowed = []) {
  for (let index = 0; index < value.length; index += 1) {
    const code = value.charCodeAt(index);
    const isControl = (code < 32 && !allowed.includes(code)) || code === 127;
    if (isControl) return true;
  }
  return false;
}

export function validateUsername(raw) {
  const username = String(raw || '').trim().replace(/^@/, '').toLowerCase();
  if (!username) return 'Choose a username';
  if (username.length < 3) return 'Usernames are at least 3 characters';
  if (username.length > 32) return 'Usernames are at most 32 characters';
  if (!USERNAME_PATTERN.test(username)) {
    return 'Use lowercase letters, numbers and underscores (no leading/trailing underscore)';
  }
  if (username.includes('__')) return 'Usernames cannot contain double underscores';
  if (RESERVED.has(username)) return 'That username is reserved';
  return null;
}

export function validateDisplayName(raw) {
  const value = String(raw || '').trim();
  if (!value) return 'Add a display name';
  if (value.length > 64) return 'Display names are at most 64 characters';
  if (hasControlCharacters(value)) return 'Display names cannot contain control characters';
  return null;
}

export function validateEmail(raw) {
  const value = String(raw || '').trim();
  if (!value) return 'Enter your email address';
  if (value.length > 254) return 'That email address is too long';
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) return 'Enter a valid email address';
  return null;
}

export function validatePassword(raw) {
  const value = String(raw || '');
  if (!value) return 'Choose a password';
  if (value.length < MIN_PASSWORD_LENGTH) {
    return `Passwords are at least ${MIN_PASSWORD_LENGTH} characters`;
  }
  if (value.length > MAX_PASSWORD_LENGTH) return `Passwords are at most ${MAX_PASSWORD_LENGTH} characters`;
  if (new Set(value).size < 6) return 'Use at least 6 different characters';
  return null;
}

export function validateMessage(raw) {
  const value = String(raw || '');
  if (!value.trim()) return 'Type a message first';
  if (value.length > MAX_MESSAGE_LENGTH) {
    return `Messages are at most ${MAX_MESSAGE_LENGTH} characters (${value.length} now)`;
  }
  // Tab (9) and newline (10) are legitimate in a message; every other control character is not.
  if (hasControlCharacters(value, [9, 10])) {
    return 'Messages cannot contain control characters';
  }
  return null;
}
