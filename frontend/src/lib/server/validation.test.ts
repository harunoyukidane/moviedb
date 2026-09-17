import { describe, expect, it } from 'vitest';
import { isFieldError, validateOptionalDate, validateOptionalInt, validateVersion } from './validation';

describe('validateOptionalDate', () => {
  it('accepts blank/absent as null', () => {
    expect(validateOptionalDate('', 'releaseDate')).toEqual({ value: null });
    expect(validateOptionalDate(undefined, 'releaseDate')).toEqual({ value: null });
    expect(validateOptionalDate('   ', 'releaseDate')).toEqual({ value: null });
  });

  it('accepts a well-formed ISO date', () => {
    expect(validateOptionalDate('2026-04-04', 'birthDate')).toEqual({ value: '2026-04-04' });
  });

  it('rejects a non-ISO format, naming the offending value', () => {
    const result = validateOptionalDate('3/3/52452242', 'releaseDate');
    expect(isFieldError(result)).toBe(true);
    if (isFieldError(result)) {
      expect(result.error.field).toBe('releaseDate');
      expect(result.error.message).toContain('3/3/52452242');
    }
  });

  it('rejects a calendar-invalid date even in the right format', () => {
    const result = validateOptionalDate('2026-02-30', 'birthDate');
    expect(isFieldError(result)).toBe(true);
  });
});

describe('validateOptionalInt', () => {
  it('accepts blank/absent as null', () => {
    expect(validateOptionalInt('', 'runtimeMinutes')).toEqual({ value: null });
    expect(validateOptionalInt(undefined, 'runtimeMinutes')).toEqual({ value: null });
  });

  it('accepts a valid integer', () => {
    expect(validateOptionalInt('120', 'runtimeMinutes')).toEqual({ value: 120 });
  });

  it('rejects a non-numeric value instead of silently dropping it', () => {
    const result = validateOptionalInt('abc', 'runtimeMinutes');
    expect(isFieldError(result)).toBe(true);
    if (isFieldError(result)) {
      expect(result.error.field).toBe('runtimeMinutes');
    }
  });
});

describe('validateVersion', () => {
  it('defaults a blank/absent version to 0', () => {
    expect(validateVersion('')).toEqual({ value: 0 });
    expect(validateVersion(undefined)).toEqual({ value: 0 });
  });

  it('accepts a valid integer version', () => {
    expect(validateVersion('3')).toEqual({ value: 3 });
  });

  it('rejects a non-integer version instead of sending NaN', () => {
    const result = validateVersion('abc');
    expect(isFieldError(result)).toBe(true);
    if (isFieldError(result)) {
      expect(result.error.field).toBe('expectedVersion');
    }
  });
});
