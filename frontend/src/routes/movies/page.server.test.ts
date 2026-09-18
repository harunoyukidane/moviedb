import { describe, expect, it, vi, beforeEach } from 'vitest';

const listMoviesMock = vi.fn();
const listGenresMock = vi.fn();
const movieTitleOffsetMock = vi.fn();

vi.mock('$lib/server/operations', () => ({
  listMovies: (...args: unknown[]) => listMoviesMock(...args),
  listGenres: (...args: unknown[]) => listGenresMock(...args),
  movieTitleOffset: (...args: unknown[]) => movieTitleOffsetMock(...args)
}));

import { load } from './+page.server';

const GENRES = [
  { code: 'HORROR', title: 'Horror', description: '', active: true },
  { code: 'COMEDY', title: 'Comedy', description: '', active: true }
];

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function makeEvent(search: string): any {
  return {
    url: new URL(`http://localhost/movies${search}`),
    request: new Request(`http://localhost/movies${search}`)
  };
}

describe('/movies load', () => {
  beforeEach(() => {
    listMoviesMock.mockReset();
    listGenresMock.mockReset();
    movieTitleOffsetMock.mockReset();
    listGenresMock.mockResolvedValue(GENRES);
    listMoviesMock.mockResolvedValue({ items: [], total: 0, limit: 24, offset: 0 });
  });

  it('queries with no filter and offset 0 by default', async () => {
    const result = (await load(makeEvent(''))) as any;
    expect(listMoviesMock).toHaveBeenCalledWith(24, 0, null, expect.anything());
    expect(result.filter).toEqual({ genreCode: null, releaseYear: null });
    expect(result.genres).toEqual(GENRES);
    expect(result.years[0]).toEqual(new Date().getFullYear() + 1);
  });

  it('passes through a genreCode that matches a loaded genre', async () => {
    const result = (await load(makeEvent('?genreCode=HORROR'))) as any;
    expect(listMoviesMock).toHaveBeenCalledWith(
      24,
      0,
      { genreCode: 'HORROR', releaseYear: null },
      expect.anything()
    );
    expect(result.filter.genreCode).toEqual('HORROR');
  });

  it('ignores an unknown genreCode instead of erroring', async () => {
    const result = (await load(makeEvent('?genreCode=GHOST'))) as any;
    expect(listMoviesMock).toHaveBeenCalledWith(24, 0, null, expect.anything());
    expect(result.filter.genreCode).toBeNull();
  });

  it('passes through a valid releaseYear and combines with genreCode (AND)', async () => {
    const result = (await load(makeEvent('?genreCode=HORROR&releaseYear=2020'))) as any;
    expect(listMoviesMock).toHaveBeenCalledWith(
      24,
      0,
      { genreCode: 'HORROR', releaseYear: 2020 },
      expect.anything()
    );
    expect(result.filter).toEqual({ genreCode: 'HORROR', releaseYear: 2020 });
  });

  it('ignores a non-numeric or out-of-range releaseYear', async () => {
    const bad = (await load(makeEvent('?releaseYear=abc'))) as any;
    expect(bad.filter.releaseYear).toBeNull();

    const tooOld = (await load(makeEvent('?releaseYear=1800'))) as any;
    expect(tooOld.filter.releaseYear).toBeNull();
  });

  it('retains filters across pagination via the offset param', async () => {
    listMoviesMock.mockResolvedValue({ items: [], total: 50, limit: 24, offset: 24 });
    const result = (await load(makeEvent('?genreCode=HORROR&offset=24'))) as any;
    expect(listMoviesMock).toHaveBeenCalledWith(
      24,
      24,
      { genreCode: 'HORROR', releaseYear: null },
      expect.anything()
    );
    expect(result.filter.genreCode).toEqual('HORROR');
  });

  it('requests the shorter list-view page size when ?view=list', async () => {
    await load(makeEvent('?view=list'));
    expect(listMoviesMock).toHaveBeenCalledWith(12, 0, null, expect.anything());
  });

  it('degrades to an empty genre list without failing the page when reference data is unavailable', async () => {
    listGenresMock.mockRejectedValue(new Error('down'));
    const result = (await load(makeEvent(''))) as any;
    expect(result.genres).toEqual([]);
    expect(result.error).toBeNull();
  });

  it('resolves the offset from movieTitleOffset when a letter param is present', async () => {
    movieTitleOffsetMock.mockResolvedValue(80);
    listMoviesMock.mockResolvedValue({ items: [], total: 300, limit: 24, offset: 80 });
    const result = (await load(makeEvent('?letter=C'))) as any;
    expect(movieTitleOffsetMock).toHaveBeenCalledWith('C', null, expect.anything());
    expect(listMoviesMock).toHaveBeenCalledWith(24, 80, null, expect.anything());
    expect(result.page.offset).toEqual(80);
  });

  it('passes the active genre/year filter through to movieTitleOffset', async () => {
    movieTitleOffsetMock.mockResolvedValue(5);
    listMoviesMock.mockResolvedValue({ items: [], total: 5, limit: 24, offset: 5 });
    await load(makeEvent('?letter=c&genreCode=HORROR&releaseYear=2020'));
    expect(movieTitleOffsetMock).toHaveBeenCalledWith(
      'C',
      { genreCode: 'HORROR', releaseYear: 2020 },
      expect.anything()
    );
  });

  it('prefers letter over a simultaneous offset param', async () => {
    movieTitleOffsetMock.mockResolvedValue(50);
    listMoviesMock.mockResolvedValue({ items: [], total: 300, limit: 24, offset: 50 });
    await load(makeEvent('?letter=D&offset=200'));
    expect(listMoviesMock).toHaveBeenCalledWith(24, 50, null, expect.anything());
  });

  it('ignores an invalid letter param and falls back to the offset param', async () => {
    listMoviesMock.mockResolvedValue({ items: [], total: 0, limit: 24, offset: 24 });
    await load(makeEvent('?letter=1&offset=24'));
    expect(movieTitleOffsetMock).not.toHaveBeenCalled();
    expect(listMoviesMock).toHaveBeenCalledWith(24, 24, null, expect.anything());
  });

  it('falls back to the last page when a letter jump lands past the end of the list', async () => {
    movieTitleOffsetMock.mockResolvedValue(300);
    listMoviesMock
      .mockResolvedValueOnce({ items: [], total: 300, limit: 24, offset: 300 })
      .mockResolvedValueOnce({
        items: [{ id: 'm1', title: 'Zodiac', synopsis: '', releaseDate: null, runtimeMinutes: null, version: 0 }],
        total: 300,
        limit: 24,
        offset: 288
      });
    const result = (await load(makeEvent('?letter=Z'))) as any;
    expect(listMoviesMock).toHaveBeenNthCalledWith(1, 24, 300, null, expect.anything());
    expect(listMoviesMock).toHaveBeenNthCalledWith(2, 24, 288, null, expect.anything());
    expect(result.page.items).toHaveLength(1);
    expect(result.page.offset).toEqual(288);
  });

  it('does not refetch when no movies match the current filter at all', async () => {
    movieTitleOffsetMock.mockResolvedValue(0);
    listMoviesMock.mockResolvedValue({ items: [], total: 0, limit: 24, offset: 0 });
    await load(makeEvent('?letter=Z'));
    expect(listMoviesMock).toHaveBeenCalledTimes(1);
  });
});
