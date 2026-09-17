// Form-input validation that runs *before* a GraphQL call is made (V2.2-04).
// Catches malformed scalars client-side, with the quoted-value message the
// backend's own scalar coercion cannot produce (it happens pre-execution, with
// no field context) — the backend guard remains the authority either way.

export interface FieldValidationError {
  field: string;
  message: string;
}

const ISO_DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

/** Strict `YYYY-MM-DD` plus a real calendar-date check (rejects e.g. 2026-02-30). */
function isValidCalendarDate(raw: string): boolean {
  const [y, m, d] = raw.split('-').map(Number);
  const date = new Date(Date.UTC(y, m - 1, d));
  return date.getUTCFullYear() === y && date.getUTCMonth() === m - 1 && date.getUTCDate() === d;
}

/** Validates an optional ISO date form field. Blank/absent is valid (null). */
export function validateOptionalDate(
  raw: string | null | undefined,
  field: string
): { value: string | null } | { error: FieldValidationError } {
  const trimmed = raw?.trim() ?? '';
  if (!trimmed) return { value: null };
  if (!ISO_DATE_RE.test(trimmed) || !isValidCalendarDate(trimmed)) {
    return {
      error: {
        field,
        message: `"${trimmed}" is not a valid date. Use the date picker, or type it as YYYY-MM-DD.`
      }
    };
  }
  return { value: trimmed };
}

/** Validates an optional integer form field (e.g. runtimeMinutes). Blank/absent is valid (null). */
export function validateOptionalInt(
  raw: string | null | undefined,
  field: string
): { value: number | null } | { error: FieldValidationError } {
  const trimmed = raw?.trim() ?? '';
  if (!trimmed) return { value: null };
  if (!/^-?\d+$/.test(trimmed)) {
    return { error: { field, message: `"${trimmed}" is not a valid number.` } };
  }
  return { value: Number(trimmed) };
}

/**
 * Validates the hidden `expectedVersion` field. A missing/non-numeric value
 * means the form was tampered with or malformed rather than a user typo, so
 * the message doesn't try to name a field to highlight.
 */
export function validateVersion(raw: string | null | undefined): { value: number } | { error: FieldValidationError } {
  const trimmed = raw?.trim() ?? '';
  if (!trimmed) return { value: 0 };
  if (!/^\d+$/.test(trimmed)) {
    return {
      error: {
        field: 'expectedVersion',
        message: 'This form could not be submitted. Reload the page and try again.'
      }
    };
  }
  return { value: Number(trimmed) };
}

/** True when [result] is the error branch of a validate* return value. */
export function isFieldError<T>(
  result: { value: T } | { error: FieldValidationError }
): result is { error: FieldValidationError } {
  return 'error' in result;
}
