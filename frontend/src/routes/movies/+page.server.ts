import type { PageServerLoad } from './$types';
import { listMovies, listGenres, type MovieFilterInput } from '$lib/server/operations';
import type { GenreCode } from '$lib/server/types';
import { messageForCode } from '$lib/errors';
import { codeForError, requestContext } from '$lib/server/request';

const PAGE_SIZE = 20;

// Configured selectable range for the release-year filter; matches the
// practical span of the demo catalogue rather than the full backend bound.
const RELEASE_YEAR_MIN = 1900;
const RELEASE_YEAR_MAX = new Date().getFullYear() + 1;

function releaseYearChoices(): number[] {
  const years: number[] = [];
  for (let year = RELEASE_YEAR_MAX; year >= RELEASE_YEAR_MIN; year--) years.push(year);
  return years;
}

/** Only accept a genreCode query param that matches a currently loaded genre. */
function parseGenreCode(url: URL, genres: GenreCode[]): string | null {
  const raw = url.searchParams.get('genreCode');
  return raw && genres.some((g) => g.code === raw) ? raw : null;
}

/** Only accept a releaseYear query param that is an integer within the configured range. */
function parseReleaseYear(url: URL): number | null {
  const raw = url.searchParams.get('releaseYear');
  if (!raw) return null;
  const year = Number(raw);
  return Number.isInteger(year) && year >= RELEASE_YEAR_MIN && year <= RELEASE_YEAR_MAX ? year : null;
}

export const load: PageServerLoad = async ({ url, request }) => {
  const offset = Math.max(0, Number(url.searchParams.get('offset') ?? '0') || 0);
  const ctx = requestContext(request);

  // Degraded: an unavailable reference-data lookup just empties the genre
  // filter options rather than failing the whole listing.
  const genres = await listGenres(ctx).catch(() => [] as GenreCode[]);

  const genreCode = parseGenreCode(url, genres);
  const releaseYear = parseReleaseYear(url);
  const filter: MovieFilterInput | null = genreCode || releaseYear ? { genreCode, releaseYear } : null;
  const years = releaseYearChoices();

  try {
    const page = await listMovies(PAGE_SIZE, offset, filter, ctx);
    return { page, error: null as string | null, genres, years, filter: { genreCode, releaseYear } };
  } catch (e) {
    const code = codeForError(e);
    return {
      page: { items: [], total: 0, limit: PAGE_SIZE, offset },
      error: messageForCode(code),
      genres,
      years,
      filter: { genreCode, releaseYear }
    };
  }
};
