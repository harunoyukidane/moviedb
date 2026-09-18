import { describe, expect, it, vi, beforeEach } from 'vitest';

const updateMovieMock = vi.fn();
const uploadMovieArtworkMock = vi.fn();
const deleteMovieMock = vi.fn();

vi.mock('$lib/server/operations', () => ({
  updateMovie: (...args: unknown[]) => updateMovieMock(...args),
  addMovieCredit: vi.fn(),
  deleteMovie: (...args: unknown[]) => deleteMovieMock(...args),
  getMovie: vi.fn(),
  listCreditRoles: vi.fn(),
  listGenres: vi.fn(),
  listLanguages: vi.fn(),
  removeMovieCredit: vi.fn()
}));

vi.mock('$lib/server/media', () => ({
  uploadMovieArtwork: (...args: unknown[]) => uploadMovieArtworkMock(...args),
  deleteMovieArtwork: vi.fn()
}));

import { actions } from './+page.server';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function makeActionEvent(fields: Record<string, string>): any {
  const form = new FormData();
  for (const [k, v] of Object.entries(fields)) form.set(k, v);
  return {
    params: { id: 'm1' },
    request: { formData: async () => form, headers: new Headers() }
  };
}

describe('/movies/[id]/edit update action', () => {
  beforeEach(() => {
    updateMovieMock.mockReset();
    uploadMovieArtworkMock.mockReset();
    deleteMovieMock.mockReset();
  });

  it('rejects a malformed release date before calling GraphQL, naming the value', async () => {
    const result = (await actions.update(
      makeActionEvent({ title: 'Inception', releaseDate: '3/3/52452242', expectedVersion: '2' })
    )) as any;
    expect(updateMovieMock).not.toHaveBeenCalled();
    expect(result.status).toBe(400);
    expect(result.data.fieldErrors.releaseDate).toContain('3/3/52452242');
  });

  it('reports a non-integer runtime rather than silently dropping it', async () => {
    const result = (await actions.update(
      makeActionEvent({ title: 'Inception', runtimeMinutes: 'abc', expectedVersion: '2' })
    )) as any;
    expect(updateMovieMock).not.toHaveBeenCalled();
    expect(result.status).toBe(400);
    expect(result.data.fieldErrors.runtimeMinutes).toBeDefined();
  });

  it('rejects a tampered/non-integer expectedVersion instead of sending NaN', async () => {
    const result = (await actions.update(
      makeActionEvent({ title: 'Inception', expectedVersion: 'not-a-number' })
    )) as any;
    expect(updateMovieMock).not.toHaveBeenCalled();
    expect(result.status).toBe(400);
  });

  it('passes valid fields through to the mutation with the parsed version', async () => {
    updateMovieMock.mockResolvedValue({});
    const result = (await actions.update(
      makeActionEvent({ title: 'Inception', releaseDate: '2010-07-16', runtimeMinutes: '148', expectedVersion: '3' })
    )) as any;
    expect(updateMovieMock).toHaveBeenCalledWith(
      'm1',
      3,
      expect.objectContaining({ releaseDate: '2010-07-16', runtimeMinutes: 148 }),
      expect.anything()
    );
    expect(result).toEqual({ updated: true });
  });

  it('returns values on a backend failure so the form can re-render what the user typed (F16)', async () => {
    const { GraphQlRequestError } = await import('$lib/server/graphql');
    updateMovieMock.mockRejectedValue(new GraphQlRequestError('BAD_USER_INPUT', 'title must not be blank', 'cid'));
    const result = (await actions.update(
      makeActionEvent({ title: 'A New Title', synopsis: 'A synopsis.', expectedVersion: '3' })
    )) as any;
    expect(result.status).toBe(400);
    expect(result.data.values).toEqual(
      expect.objectContaining({ title: 'A New Title', synopsis: 'A synopsis.' })
    );
  });

  it('maps a dependency outage to 503, not 409 (F12)', async () => {
    const { GraphQlRequestError } = await import('$lib/server/graphql');
    updateMovieMock.mockRejectedValue(new GraphQlRequestError('DEPENDENCY_UNAVAILABLE', 'unavailable', 'cid'));
    const result = (await actions.update(
      makeActionEvent({ title: 'Inception', expectedVersion: '3' })
    )) as any;
    expect(result.status).toBe(503);
  });

  it('only changed fields are sent in the update mask (F21)', async () => {
    updateMovieMock.mockResolvedValue({});
    const form = new FormData();
    form.set('expectedVersion', '3');
    form.set('base.title', 'Original Title');
    form.set('title', 'Original Title'); // unchanged
    form.set('base.synopsis', 'Old synopsis.');
    form.set('synopsis', 'New synopsis.'); // changed
    form.append('base.genreCodes', 'HORROR');
    form.append('genreCodes', 'HORROR'); // unchanged, just reordered submission not applicable here

    await actions.update({
      params: { id: 'm1' },
      request: { formData: async () => form, headers: new Headers() }
    } as any);

    expect(updateMovieMock).toHaveBeenCalledWith(
      'm1',
      3,
      { synopsis: 'New synopsis.' },
      expect.anything()
    );
  });

  it('a no-op submit (nothing changed) skips the mutation entirely', async () => {
    const form = new FormData();
    form.set('expectedVersion', '3');
    form.set('base.title', 'Same Title');
    form.set('title', 'Same Title');

    const result = (await actions.update({
      params: { id: 'm1' },
      request: { formData: async () => form, headers: new Headers() }
    } as any)) as any;

    expect(updateMovieMock).not.toHaveBeenCalled();
    expect(result).toEqual({ updated: true });
  });

  it('maps a genuine conflict to 409', async () => {
    const { GraphQlRequestError } = await import('$lib/server/graphql');
    updateMovieMock.mockRejectedValue(new GraphQlRequestError('CONFLICT', 'stale version', 'cid'));
    const result = (await actions.update(
      makeActionEvent({ title: 'Inception', expectedVersion: '3' })
    )) as any;
    expect(result.status).toBe(409);
  });
});

describe('/movies/[id]/edit uploadArtwork action', () => {
  beforeEach(() => {
    uploadMovieArtworkMock.mockReset();
  });

  function makeUploadEvent(file: File | null): any {
    const form = new FormData();
    if (file) form.set('file', file);
    return {
      params: { id: 'm1' },
      request: { formData: async () => form, headers: new Headers() }
    };
  }

  it('the empty-file guard returns a specific message, not the generic banner (F13)', async () => {
    const result = (await actions.uploadArtwork(makeUploadEvent(null))) as any;
    expect(result.status).toBe(400);
    expect(result.data.message).toBe('Choose an image file first.');
    expect(uploadMovieArtworkMock).not.toHaveBeenCalled();
  });

  it('an upload failure maps each code to its own status, not 415 for everything (F11)', async () => {
    const { GraphQlRequestError } = await import('$lib/server/graphql');
    uploadMovieArtworkMock.mockRejectedValue(
      new GraphQlRequestError('STORAGE_UNAVAILABLE', 'storage down', 'cid')
    );
    const file = new File(['x'], 'poster.png', { type: 'image/png' });
    const result = (await actions.uploadArtwork(makeUploadEvent(file))) as any;
    expect(result.status).toBe(503);
  });
});

describe('/movies/[id]/edit delete action', () => {
  beforeEach(() => {
    deleteMovieMock.mockReset();
  });

  it('maps NOT_FOUND to 404, not 503 (F12)', async () => {
    const { GraphQlRequestError } = await import('$lib/server/graphql');
    deleteMovieMock.mockRejectedValue(new GraphQlRequestError('NOT_FOUND', 'movie not found', 'cid'));
    const result = (await actions.delete({
      params: { id: 'ghost' },
      request: { formData: async () => new FormData(), headers: new Headers() }
    } as any)) as any;
    expect(result.status).toBe(404);
  });
});
