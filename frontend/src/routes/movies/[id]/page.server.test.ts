import { describe, expect, it, vi, beforeEach } from 'vitest';

const getMovieMock = vi.fn();
const listCommentsMock = vi.fn();
const addMovieCommentMock = vi.fn();

vi.mock('$lib/server/operations', () => ({
  getMovie: (...args: unknown[]) => getMovieMock(...args),
  listComments: (...args: unknown[]) => listCommentsMock(...args),
  addMovieComment: (...args: unknown[]) => addMovieCommentMock(...args)
}));

import { load, actions } from './+page.server';

const MOVIE = { id: 'm1', title: 'Inception', cast: [], creators: [], genres: [] };
const EMPTY_COMMENTS = { items: [], total: 0, limit: 10, offset: 0 };

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function makeLoadEvent(search = ''): any {
  return {
    params: { id: 'm1' },
    url: new URL(`http://localhost/movies/m1${search}`),
    request: new Request(`http://localhost/movies/m1${search}`)
  };
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function makeActionEvent(fields: Record<string, string>): any {
  const form = new FormData();
  for (const [k, v] of Object.entries(fields)) form.set(k, v);
  return {
    params: { id: 'm1' },
    request: { formData: async () => form, headers: new Headers() }
  };
}

describe('/movies/[id] load', () => {
  beforeEach(() => {
    getMovieMock.mockReset();
    listCommentsMock.mockReset();
    getMovieMock.mockResolvedValue(MOVIE);
    listCommentsMock.mockResolvedValue(EMPTY_COMMENTS);
  });

  it('loads the movie and the first page of comments by default', async () => {
    const result = (await load(makeLoadEvent())) as any;
    expect(result.movie).toEqual(MOVIE);
    expect(listCommentsMock).toHaveBeenCalledWith('m1', 10, 0, expect.anything());
    expect(result.comments).toEqual(EMPTY_COMMENTS);
  });

  it('passes through a valid commentsOffset', async () => {
    await load(makeLoadEvent('?commentsOffset=10'));
    expect(listCommentsMock).toHaveBeenCalledWith('m1', 10, 10, expect.anything());
  });

  it('ignores an invalid commentsOffset and falls back to 0', async () => {
    await load(makeLoadEvent('?commentsOffset=-5'));
    expect(listCommentsMock).toHaveBeenCalledWith('m1', 10, 0, expect.anything());

    listCommentsMock.mockClear();
    await load(makeLoadEvent('?commentsOffset=abc'));
    expect(listCommentsMock).toHaveBeenCalledWith('m1', 10, 0, expect.anything());
  });

  it('degrades to an empty comment page without failing the whole load when comments are unavailable', async () => {
    listCommentsMock.mockRejectedValue(new Error('down'));
    const result = (await load(makeLoadEvent())) as any;
    expect(result.movie).toEqual(MOVIE);
    expect(result.comments).toEqual({ items: [], total: 0, limit: 10, offset: 0 });
  });
});

describe('/movies/[id] addComment action', () => {
  beforeEach(() => {
    addMovieCommentMock.mockReset();
  });

  it('trims fields and adds the comment', async () => {
    addMovieCommentMock.mockResolvedValue({ id: 'c1', authorDisplayName: 'Alice', text: 'Hi', createdAt: '2026-01-01T00:00:00Z' });
    const result = (await actions.addComment(
      makeActionEvent({ authorDisplayName: '  Alice  ', text: '  Hi  ' })
    )) as any;
    expect(addMovieCommentMock).toHaveBeenCalledWith(
      'm1',
      { authorDisplayName: 'Alice', text: 'Hi' },
      expect.anything()
    );
    expect(result).toEqual({ commentAdded: true });
  });

  it('maps a validation failure to a 400 with a friendly message', async () => {
    const err = new Error('blank');
    (err as any).code = 'BAD_USER_INPUT';
    const { GraphQlRequestError } = await import('$lib/server/graphql');
    addMovieCommentMock.mockRejectedValue(new GraphQlRequestError('BAD_USER_INPUT', 'blank', 'corr-1'));

    const result = (await actions.addComment(makeActionEvent({ authorDisplayName: '', text: 'Hi' }))) as any;
    expect(result.status).toBe(400);
    expect(result.data.section).toBe('comment');
  });

  it('maps an unknown-movie failure to a 404, not 503 (F12)', async () => {
    const { GraphQlRequestError } = await import('$lib/server/graphql');
    addMovieCommentMock.mockRejectedValue(new GraphQlRequestError('NOT_FOUND', 'gone', 'corr-2'));

    const result = (await actions.addComment(makeActionEvent({ authorDisplayName: 'Alice', text: 'Hi' }))) as any;
    expect(result.status).toBe(404);
    expect(result.data.section).toBe('comment');
  });

  it('maps a dependency outage to a 503', async () => {
    const { GraphQlRequestError } = await import('$lib/server/graphql');
    addMovieCommentMock.mockRejectedValue(new GraphQlRequestError('DEPENDENCY_UNAVAILABLE', 'down', 'corr-3'));

    const result = (await actions.addComment(makeActionEvent({ authorDisplayName: 'Alice', text: 'Hi' }))) as any;
    expect(result.status).toBe(503);
    expect(result.data.section).toBe('comment');
  });
});
