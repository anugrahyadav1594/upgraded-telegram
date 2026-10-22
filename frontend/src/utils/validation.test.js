import { describe, expect, it } from 'vitest';

import {
  MAX_MESSAGE_LENGTH,
  hasControlCharacters,
  validateDisplayName,
  validateEmail,
  validateMessage,
  validatePassword,
  validateUsername,
} from './validation.js';

describe('validateUsername', () => {
  it('accepts handles the server accepts', () => {
    expect(validateUsername('alex')).toBeNull();
    expect(validateUsername('@ghost_2026')).toBeNull();
  });

  it('rejects reserved and malformed handles', () => {
    expect(validateUsername('admin')).toMatch(/reserved/i);
    expect(validateUsername('a')).toMatch(/at least 3/i);
    expect(validateUsername('_alex')).toMatch(/lowercase letters/i);
    expect(validateUsername('alex__y')).toMatch(/double underscores/i);
  });

  it('normalises case and a leading @ before validating', () => {
    expect(validateUsername('  @Developer ')).toBeNull();
  });
});

describe('validatePassword', () => {
  it('requires at least twelve characters', () => {
    expect(validatePassword('short')).toMatch(/at least 12/i);
    expect(validatePassword('correct horse battery staple')).toBeNull();
  });

  it('rejects a password that is long but nearly uniform', () => {
    expect(validatePassword('aaaaaaaaaaaa')).toMatch(/different characters/i);
  });
});

describe('validateMessage', () => {
  it('rejects empty and whitespace-only messages', () => {
    expect(validateMessage('   ')).toMatch(/type a message/i);
  });

  it('enforces the server length limit', () => {
    expect(validateMessage('a'.repeat(MAX_MESSAGE_LENGTH + 1))).toMatch(/at most/i);
  });

  it('allows newlines and tabs but not other control characters', () => {
    expect(validateMessage('line one\nline two')).toBeNull();
    expect(validateMessage('tab\there')).toBeNull();
    expect(hasControlCharacters('bell\u0007')).toBe(true);
  });
});

describe('validateEmail and validateDisplayName', () => {
  it('accepts a normal address and rejects a malformed one', () => {
    expect(validateEmail('someone@example.test')).toBeNull();
    expect(validateEmail('not-an-address')).toMatch(/valid email/i);
  });

  it('keeps display names free of control characters', () => {
    expect(validateDisplayName('Alex')).toBeNull();
    expect(validateDisplayName('Alex\u0000')).toMatch(/control characters/i);
    expect(validateDisplayName('')).toMatch(/add a display name/i);
  });
});
