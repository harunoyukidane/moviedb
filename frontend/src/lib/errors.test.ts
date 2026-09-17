import { describe, expect, it } from 'vitest';
import { isErrorCode, isValidationError, messageForCode, messageForValidation, type ErrorCode } from './errors';

describe('error boundary', () => {
  const codes: ErrorCode[] = [
    'BAD_USER_INPUT',
    'NOT_FOUND',
    'CONFLICT',
    'PERSON_IN_USE',
    'PAYLOAD_TOO_LARGE',
    'UNSUPPORTED_MEDIA_TYPE',
    'DEPENDENCY_UNAVAILABLE',
    'INTERNAL_ERROR'
  ];

  it('maps every known code to a distinct, non-empty message', () => {
    const messages = codes.map((c) => messageForCode(c));
    for (const m of messages) expect(m.length).toBeGreaterThan(0);
    expect(new Set(messages).size).toBe(codes.length);
  });

  it('PERSON_IN_USE message guides removing credits first', () => {
    expect(messageForCode('PERSON_IN_USE')).toMatch(/credit/i);
  });

  it('CONFLICT message mentions reloading', () => {
    expect(messageForCode('CONFLICT')).toMatch(/reload/i);
  });

  it('PAYLOAD_TOO_LARGE mentions the size limit', () => {
    expect(messageForCode('PAYLOAD_TOO_LARGE')).toMatch(/5 MB|too large/i);
  });

  it('UNSUPPORTED_MEDIA_TYPE lists allowed formats', () => {
    expect(messageForCode('UNSUPPORTED_MEDIA_TYPE')).toMatch(/JPEG|PNG|WebP/i);
  });

  it('unknown or missing codes fall back to the internal-error message', () => {
    expect(messageForCode('SOMETHING_ELSE')).toBe(messageForCode('INTERNAL_ERROR'));
    expect(messageForCode(undefined)).toBe(messageForCode('INTERNAL_ERROR'));
    expect(messageForCode(null)).toBe(messageForCode('INTERNAL_ERROR'));
  });

  it('isErrorCode and isValidationError classify correctly', () => {
    expect(isErrorCode('CONFLICT')).toBe(true);
    expect(isErrorCode('NOPE')).toBe(false);
    expect(isValidationError('BAD_USER_INPUT')).toBe(true);
    expect(isValidationError('CONFLICT')).toBe(false);
  });

  it('messageForValidation prefers the server message for BAD_USER_INPUT', () => {
    expect(messageForValidation('BAD_USER_INPUT', 'title must be at most 300 characters')).toBe(
      'title must be at most 300 characters'
    );
  });

  it('messageForValidation falls back to the generic copy when no server message is present', () => {
    expect(messageForValidation('BAD_USER_INPUT', undefined)).toBe(messageForCode('BAD_USER_INPUT'));
    expect(messageForValidation('BAD_USER_INPUT', null)).toBe(messageForCode('BAD_USER_INPUT'));
  });

  it('messageForValidation ignores the server message for non-validation codes', () => {
    expect(messageForValidation('CONFLICT', 'some internal detail')).toBe(messageForCode('CONFLICT'));
  });
});
