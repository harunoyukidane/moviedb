import { describe, expect, it } from 'vitest';
import {
  humanizeValidationMessage,
  isErrorCode,
  isValidationError,
  messageForCode,
  messageForValidation,
  type ErrorCode
} from './errors';

describe('error boundary', () => {
  const codes: ErrorCode[] = [
    'BAD_USER_INPUT',
    'NOT_FOUND',
    'CONFLICT',
    'PERSON_IN_USE',
    'PAYLOAD_TOO_LARGE',
    'UNSUPPORTED_MEDIA_TYPE',
    'STORAGE_UNAVAILABLE',
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

  it('STORAGE_UNAVAILABLE has its own message rather than falling through to the internal-error copy', () => {
    expect(messageForCode('STORAGE_UNAVAILABLE')).not.toBe(messageForCode('INTERNAL_ERROR'));
    expect(messageForCode('STORAGE_UNAVAILABLE')).toMatch(/temporarily unavailable/i);
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

  it('messageForValidation prefers the server message for BAD_USER_INPUT, rewritten for display', () => {
    expect(messageForValidation('BAD_USER_INPUT', 'title must be at most 300 characters')).toBe(
      'Title can be up to 300 characters.'
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

describe('humanizeValidationMessage', () => {
  it('rewrites a blank-field message with the field label, not the raw key', () => {
    expect(humanizeValidationMessage('authorDisplayName must not be blank')).toBe('Your name is required.');
    expect(humanizeValidationMessage('name must not be blank')).toBe('Name is required.');
    expect(humanizeValidationMessage('text must not be blank')).toBe('Comment is required.');
  });

  it('rewrites a max-length message using the field label and, when present, the entered count', () => {
    expect(humanizeValidationMessage('title must be at most 300 characters')).toBe(
      'Title can be up to 300 characters.'
    );
    expect(humanizeValidationMessage('synopsis must be at most 5000 characters — you entered 5120.')).toBe(
      'Synopsis can be up to 5000 characters — you entered 5120.'
    );
  });

  it('collapses every unstorable-character shape into one plain-language message, without the technical detail', () => {
    const expected =
      "Comment contains a character that can't be saved — this can happen when text is pasted from another app. Delete the affected part and retype it.";
    expect(humanizeValidationMessage("text contains a null character, which can't be stored.")).toBe(expected);
    expect(humanizeValidationMessage("text contains an invisible character, which isn't allowed.")).toBe(expected);
    expect(
      humanizeValidationMessage("text contains a text-direction override character, which isn't allowed.")
    ).toBe(expected);
    expect(humanizeValidationMessage('text contains a control character at position 12.')).toBe(expected);
  });

  it('rewrites the emoji rule using the field label', () => {
    expect(humanizeValidationMessage("characterName can't contain emoji.")).toBe(
      "Character name can't contain emoji."
    );
  });

  it('rewrites negative-number rules in plain language', () => {
    expect(humanizeValidationMessage('runtimeMinutes must be positive')).toBe('Runtime must be greater than zero.');
    expect(humanizeValidationMessage('billingOrder must be zero or positive')).toBe(
      "Billing order can't be negative."
    );
  });

  it('rewrites cast/crew character-name rules without naming the raw field', () => {
    expect(humanizeValidationMessage('cast credits require a characterName')).toBe(
      'Cast credits need a character name.'
    );
    expect(humanizeValidationMessage('crew credits must not have a characterName')).toBe(
      "Crew credits can't have a character name."
    );
  });

  it('rewrites a stale reference-code error without echoing the raw code value', () => {
    expect(humanizeValidationMessage("genre code 'xyz' does not exist")).toBe(
      'That genre is no longer available. Please choose another.'
    );
    expect(humanizeValidationMessage("role code 'director' is inactive")).toBe(
      'That role is no longer active. Please choose another.'
    );
  });

  it('leaves an already-natural backend sentence alone', () => {
    const message = 'Release date 1850-01-01 is before the first film was made (14 October 1888).';
    expect(humanizeValidationMessage(message)).toBe(message);
  });

  it('capitalizes and punctuates an unrecognized message rather than passing it through verbatim', () => {
    expect(humanizeValidationMessage('something unexpected happened')).toBe('Something unexpected happened.');
  });
});
