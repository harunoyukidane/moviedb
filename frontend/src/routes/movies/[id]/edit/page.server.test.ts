import { describe, expect, it, vi, beforeEach } from 'vitest';

const updateMovieMock = vi.fn();

vi.mock('$lib/server/operations', () => ({
  updateMovie: (...args: unknown[]) => updateMovieMock(...args),
  addMovieCredit: vi.fn(),
  deleteMovie: vi.fn(),
  getMovie: vi.fn(),
  listCreditRoles: vi.fn(),
  listGenres: vi.fn(),
  listLanguages: vi.fn(),
  removeMovieCredit: vi.fn()
}));

vi.mock('$lib/server/media', () => ({
  uploadMovieArtwork: vi.fn(),
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
});
