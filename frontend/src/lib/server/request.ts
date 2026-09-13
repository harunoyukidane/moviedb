import { error } from '@sveltejs/kit';
import { messageForCode } from '$lib/errors';
import { GraphQlRequestError, type RequestContext } from './graphql';

export function requestContext(request: Request): RequestContext {
  return { correlationId: request.headers.get('x-correlation-id') ?? undefined };
}

export function codeForError(cause: unknown): string {
  return cause instanceof GraphQlRequestError ? cause.code : 'INTERNAL_ERROR';
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
