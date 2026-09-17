import { describe, expect, it, vi, beforeEach } from 'vitest';

const createMovieMock = vi.fn();

vi.mock('$lib/server/operations', () => ({
  createMovie: (...args: unknown[]) => createMovieMock(...args),
  listGenres: vi.fn().mockResolvedValue([]),
  listLanguages: vi.fn().mockResolvedValue([])
}));

import { actions } from './+page.server';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function makeActionEvent(fields: Record<string, string>): any {
  const form = new FormData();
  for (const [k, v] of Object.entries(fields)) form.set(k, v);
  return { request: { formData: async () => form, headers: new Headers() } };
}

describe('/movies/new default action', () => {
  beforeEach(() => {
    createMovieMock.mockReset();
  });

  it('rejects a malformed release date before calling GraphQL, naming the value', async () => {
    const result = (await actions.default(
      makeActionEvent({ title: 'Inception', releaseDate: '3/3/52452242' })
    )) as any;
    expect(createMovieMock).not.toHaveBeenCalled();
    expect(result.status).toBe(400);
    expect(result.data.fieldErrors.releaseDate).toContain('3/3/52452242');
    expect(result.data.values.title).toBe('Inception');
  });

  it('reports a non-integer runtime rather than silently dropping it', async () => {
    const result = (await actions.default(
      makeActionEvent({ title: 'Inception', runtimeMinutes: 'abc' })
    )) as any;
    expect(createMovieMock).not.toHaveBeenCalled();
    expect(result.status).toBe(400);
    expect(result.data.fieldErrors.runtimeMinutes).toBeDefined();
  });

  it('passes a valid release date and runtime through to the mutation', async () => {
    createMovieMock.mockResolvedValue({ id: 'm1' });
    await expect(
      actions.default(
        makeActionEvent({ title: 'Inception', releaseDate: '2010-07-16', runtimeMinutes: '148' })
      )
    ).rejects.toMatchObject({ status: 303 });
    expect(createMovieMock).toHaveBeenCalledWith(
      expect.objectContaining({ releaseDate: '2010-07-16', runtimeMinutes: 148 }),
      expect.anything()
    );
  });
});
