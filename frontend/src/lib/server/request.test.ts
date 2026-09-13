import { describe, expect, it } from 'vitest';
import { GraphQlRequestError } from './graphql';
import { codeForError, requestContext } from './request';

describe('BFF request helpers', () => {
  it('forwards an inbound correlation id', () => {
    const request = new Request('http://frontend', { headers: { 'x-correlation-id': 'trace-1' } });
    expect(requestContext(request)).toEqual({ correlationId: 'trace-1' });
  });

  it('normalizes expected and unexpected errors', () => {
    expect(codeForError(new GraphQlRequestError('NOT_FOUND', 'missing', 'trace-1'))).toBe('NOT_FOUND');
    expect(codeForError(new Error('boom'))).toBe('INTERNAL_ERROR');
  });
});
