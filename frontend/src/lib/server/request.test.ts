import { describe, expect, it } from 'vitest';
import { GraphQlRequestError } from './graphql';
import { codeForError, fieldErrorsForError, messageForError, requestContext } from './request';

describe('BFF request helpers', () => {
  it('forwards an inbound correlation id', () => {
    const request = new Request('http://frontend', { headers: { 'x-correlation-id': 'trace-1' } });
    expect(requestContext(request)).toEqual({ correlationId: 'trace-1' });
  });

  it('normalizes expected and unexpected errors', () => {
    expect(codeForError(new GraphQlRequestError('NOT_FOUND', 'missing', 'trace-1'))).toBe('NOT_FOUND');
    expect(codeForError(new Error('boom'))).toBe('INTERNAL_ERROR');
  });

  it('messageForError surfaces the backend validation message', () => {
    const e = new GraphQlRequestError('BAD_USER_INPUT', 'title must be at most 300 characters', 'trace-1', 'title');
    expect(messageForError(e, 'BAD_USER_INPUT')).toBe('title must be at most 300 characters');
  });

  it('messageForError falls back to the generic copy for a non-GraphQL error', () => {
    expect(messageForError(new Error('boom'), 'INTERNAL_ERROR')).not.toMatch(/boom/);
  });

  it('fieldErrorsForError keys the message to the offending field', () => {
    const e = new GraphQlRequestError('BAD_USER_INPUT', 'title must be at most 300 characters', 'trace-1', 'title');
    expect(fieldErrorsForError(e)).toEqual({ title: 'title must be at most 300 characters' });
  });

  it('fieldErrorsForError is undefined when the error carries no field', () => {
    expect(fieldErrorsForError(new GraphQlRequestError('CONFLICT', 'stale', 'trace-1'))).toBeUndefined();
    expect(fieldErrorsForError(new Error('boom'))).toBeUndefined();
  });
});
