// The single boundary translating stable GraphQL/media `extensions.code` values
// (§8.2) into friendly, human messages. Every screen uses this — never ad hoc
// per-component copy. Pure (no server imports) so it is usable on both sides.

export type ErrorCode =
  | 'BAD_USER_INPUT'
  | 'NOT_FOUND'
  | 'CONFLICT'
  | 'PERSON_IN_USE'
  | 'PAYLOAD_TOO_LARGE'
  | 'UNSUPPORTED_MEDIA_TYPE'
  | 'DEPENDENCY_UNAVAILABLE'
  | 'INTERNAL_ERROR';

const MESSAGES: Record<ErrorCode, string> = {
  BAD_USER_INPUT: 'Please correct the highlighted field and try again.',
  NOT_FOUND: "We couldn't find what you were looking for.",
  CONFLICT:
    'This was changed by someone else since you loaded it. Reload to get the latest version, then reapply your changes.',
  PERSON_IN_USE:
    'This person is still credited on one or more movies. Remove those credits first, then delete the person.',
  PAYLOAD_TOO_LARGE: 'That image is too large. Please choose a file up to 5 MB.',
  UNSUPPORTED_MEDIA_TYPE: 'That file is not a supported image. Please upload a JPEG, PNG, or WebP.',
  DEPENDENCY_UNAVAILABLE:
    'A required service is temporarily unavailable. Please try again in a moment.',
  INTERNAL_ERROR: 'Something went wrong on our end. Please try again.'
};

/** True when [code] is one of the known stable codes. */
export function isErrorCode(code: string): code is ErrorCode {
  return code in MESSAGES;
}

/** Map a stable code to a friendly message, defaulting to the internal-error copy. */
export function messageForCode(code: string | undefined | null): string {
  if (code && isErrorCode(code)) return MESSAGES[code];
  return MESSAGES.INTERNAL_ERROR;
}

/** Whether a code represents a user-fixable validation problem (surfaced inline). */
export function isValidationError(code: string | undefined | null): boolean {
  return code === 'BAD_USER_INPUT';
}

/**
 * The message to show for a validation failure: the backend's own curated
 * message when one is present (it already names the offending value and rule),
 * falling back to the generic copy otherwise.
 */
export function messageForValidation(
  code: string | undefined | null,
  serverMessage: string | undefined | null
): string {
  if (code === 'BAD_USER_INPUT' && serverMessage) return serverMessage;
  return messageForCode(code);
}
