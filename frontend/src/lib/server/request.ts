import { error } from '@sveltejs/kit';
import { messageForCode, messageForValidation } from '$lib/errors';
import { GraphQlRequestError, type RequestContext } from './graphql';

export function requestContext(request: Request): RequestContext {
  return { correlationId: request.headers.get('x-correlation-id') ?? undefined };
}

export function codeForError(cause: unknown): string {
  return cause instanceof GraphQlRequestError ? cause.code : 'INTERNAL_ERROR';
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
 */
export function fieldErrorsForError(cause: unknown): Record<string, string> | undefined {
  if (cause instanceof GraphQlRequestError && cause.field) {
    return { [cause.field]: cause.message };
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
