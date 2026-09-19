import { error } from '@sveltejs/kit';
import { humanizeValidationMessage, messageForCode, messageForValidation } from '$lib/errors';
import { GraphQlRequestError, type RequestContext } from './graphql';

export function requestContext(request: Request): RequestContext {
  return { correlationId: request.headers.get('x-correlation-id') ?? undefined };
}

export function codeForError(cause: unknown): string {
  return cause instanceof GraphQlRequestError ? cause.code : 'INTERNAL_ERROR';
}

/**
 * The one HTTP status map every form action shares (V2.2-08), so an outage
 * during upload isn't reported as 415 (F11) and a not-found delete isn't
 * reported as 503 (F12) - every action asks this single source of truth
 * instead of hand-rolling its own ternary.
 */
export function statusForCode(code: string): number {
  switch (code) {
    case 'BAD_USER_INPUT':
    case 'PERSON_DATE_CONFLICTS_CREDIT':
      return 400;
    case 'NOT_FOUND':
      return 404;
    case 'CONFLICT':
    case 'PERSON_IN_USE':
      return 409;
    case 'PAYLOAD_TOO_LARGE':
      return 413;
    case 'UNSUPPORTED_MEDIA_TYPE':
      return 415;
    case 'STORAGE_UNAVAILABLE':
    case 'DEPENDENCY_UNAVAILABLE':
      return 503;
    default:
      return 500;
  }
}

/**
 * The specific message to show the user for [cause] — the backend's own
 * curated validation message when present, falling back to the generic copy
 * for [code].
 */
export function messageForError(cause: unknown, code: string): string {
  const serverMessage = cause instanceof GraphQlRequestError ? cause.message : undefined;
  return messageForValidation(code, serverMessage);
}

/**
 * A single-entry field-error map keyed to the GraphQL field the backend named,
 * for wiring `aria-invalid`/`aria-describedby` on the offending form control.
 * Undefined when the error isn't attributable to one field.
 *
 * Keyed off the presence of a field rather than off the code, because
 * BAD_USER_INPUT is no longer the only code that carries one:
 * PERSON_DATE_CONFLICTS_CREDIT names birthDate or deathDate (V2.8-03). Any
 * field-attributed backend message is curated prose, so it is always safe to
 * run through the same plain-language rewrite `messageForValidation` applies
 * to the banner copy - this is what keeps the inline, per-field message from
 * showing the raw backend string underneath the field.
 */
export function fieldErrorsForError(cause: unknown): Record<string, string> | undefined {
  if (cause instanceof GraphQlRequestError && cause.field) {
    return { [cause.field]: humanizeValidationMessage(cause.message) };
  }
  return undefined;
}

export function throwPageLoadError(cause: unknown): never {
  if (cause instanceof GraphQlRequestError) {
    if (cause.code === 'NOT_FOUND') {
      throw error(404, { message: messageForCode('NOT_FOUND') });
    }
    throw error(503, { code: cause.code, message: messageForCode(cause.code) });
  }
  throw cause;
}
