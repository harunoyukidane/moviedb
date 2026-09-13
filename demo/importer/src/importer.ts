import { CONSIDERED_CREW_JOBS, CREW_JOB_MAP, GENRE_MAP, MAX_CAST } from './mappings.js';
import { InvalidTokenError, NotFoundError } from './errors.js';
import type { ArtworkPort, CataloguePort, PeoplePort } from './ports.js';
import type { TmdbClient, TmdbMovie } from './tmdb.js';

export interface MovieOutcome {
  tmdbId: number;
  status: 'imported' | 'skipped' | 'failed';
  movieId?: string;
  title?: string;
  posterImported: boolean;
  creditsImported: number;
  peopleImported: number;
  photosImported: number;
  error?: string;
}

export interface ImportReport {
  total: number;
  imported: number;
  skipped: number;
  failed: number;
  outcomes: MovieOutcome[];
}

export interface Dependencies {
  tmdb: TmdbClient;
  people: PeoplePort;
  catalogue: CataloguePort;
  artwork: ArtworkPort;
}

/**
 * Import a single movie through the application interfaces (§12.3). Idempotent by
 * tmdb ids. A failed item never rolls back prior successes. Throws only for the
 * fatal InvalidTokenError so the runner can stop the whole run.
 */
export async function importMovie(tmdbId: number, deps: Dependencies): Promise<MovieOutcome> {
  const outcome: MovieOutcome = {
    tmdbId,
    status: 'imported',
    posterImported: false,
    creditsImported: 0,
    peopleImported: 0,
    photosImported: 0
  };
  try {
    const movie = await deps.tmdb.getMovie(tmdbId);
    outcome.title = movie.title;

    // 2-3. bounded subset + genre mapping (skip unmapped, never invent codes)
    const genreCodes = mapGenres(movie);

    // 6. upsert movie + genres by tmdb_id
    const movieId = await deps.catalogue.upsertMovie({
      tmdbId: movie.id,
      title: movie.title,
      originalTitle: movie.original_title ?? null,
      synopsis: movie.overview ?? '',
      releaseDate: emptyToNull(movie.release_date),
      runtimeMinutes: movie.runtime && movie.runtime > 0 ? movie.runtime : null,
      originalLanguage: movie.original_language ?? null,
      genreCodes
    });
    outcome.movieId = movieId;

    // 2,4,5,7. selected credits -> dedup people -> upsert people -> upsert credits
    const selected = selectCredits(movie);
    const personIdByTmdb = new Map<number, string>();
    for (const c of selected) {
      let personId = personIdByTmdb.get(c.personTmdbId);
      if (!personId) {
        personId = await deps.people.upsertPerson({
          tmdbId: c.personTmdbId,
          name: c.personName,
          biography: '',
          birthDate: null,
          deathDate: null,
          placeOfBirth: null
        });
        personIdByTmdb.set(c.personTmdbId, personId);
        outcome.peopleImported++;

        // Person photo (best-effort, §13): download the TMDB profile image and
        // upload it through the person-photo path so it is served offline after
        // seeding — mirroring the movie-poster flow. This is what sets the
        // person's stored photo; a failure is non-fatal.
        if (c.profilePath) {
          const photo = await deps.tmdb.getProfileImage(c.profilePath);
          if (photo) {
            try {
              await deps.artwork.uploadPersonPhoto(personId, photo.bytes, photo.contentType);
              outcome.photosImported++;
            } catch {
              // leave the person without an uploaded photo; not fatal
            }
          }
        }
      }
      try {
        await deps.catalogue.upsertCredit({
          movieId,
          personId,
          roleCode: c.roleCode,
          characterName: c.characterName,
          billingOrder: c.billingOrder,
          tmdbCreditId: c.creditId,
          sourceRoleName: c.sourceRoleName
        });
        outcome.creditsImported++;
      } catch (e) {
        // Partial credits: one bad credit does not fail the whole movie (§13).
        // Record and continue with the rest.
      }
    }

    // 8. poster (best-effort; missing poster -> movie still imported)
    const poster = await deps.tmdb.getPoster(movie.poster_path);
    if (poster) {
      try {
        await deps.artwork.uploadMoviePoster(movieId, poster.bytes, poster.contentType);
        outcome.posterImported = true;
      } catch {
        outcome.posterImported = false;
      }
    }

    return outcome;
  } catch (e) {
    if (e instanceof InvalidTokenError) throw e; // fatal: stop the whole run
    if (e instanceof NotFoundError) {
      return { ...outcome, status: 'skipped', error: '404 not found' };
    }
    return { ...outcome, status: 'failed', error: e instanceof Error ? e.message : String(e) };
  }
}

/** Map TMDB genre ids to controlled codes; unmapped ids are dropped. */
export function mapGenres(movie: TmdbMovie): string[] {
  const codes = new Set<string>();
  for (const g of movie.genres ?? []) {
    const code = GENRE_MAP[g.id];
    if (code) codes.add(code);
  }
  return [...codes];
}

interface SelectedCredit {
  personTmdbId: number;
  personName: string;
  profilePath: string | null;
  roleCode: string;
  characterName: string | null;
  billingOrder: number | null;
  creditId: string;
  sourceRoleName: string | null;
}

/** Keep top-N cast by order + the mapped crew jobs (§12.3 step 2). */
export function selectCredits(movie: TmdbMovie): SelectedCredit[] {
  const out: SelectedCredit[] = [];
  const cast = [...(movie.credits?.cast ?? [])]
    .sort((a, b) => (a.order ?? 9999) - (b.order ?? 9999))
    .slice(0, MAX_CAST);
  for (const c of cast) {
    out.push({
      personTmdbId: c.id,
      personName: c.name,
      profilePath: c.profile_path ?? null,
      roleCode: 'ACTOR',
      characterName: c.character && c.character.trim().length > 0 ? c.character : 'Unknown role',
      billingOrder: c.order ?? null,
      creditId: c.credit_id,
      sourceRoleName: null
    });
  }
  for (const c of movie.credits?.crew ?? []) {
    if (!CONSIDERED_CREW_JOBS.has(c.job)) continue;
    const roleCode = CREW_JOB_MAP[c.job];
    if (!roleCode) continue; // considered but not mapped to a controlled code -> skip
    out.push({
      personTmdbId: c.id,
      personName: c.name,
      profilePath: c.profile_path ?? null,
      roleCode,
      characterName: null,
      billingOrder: null,
      creditId: c.credit_id,
      sourceRoleName: c.job
    });
  }
  return out;
}

function emptyToNull(s: string | undefined): string | null {
  return s && s.length > 0 ? s : null;
}

/** Run the whole manifest with bounded concurrency; aggregate outcomes. */
export async function runImport(ids: number[], deps: Dependencies, concurrency: number): Promise<ImportReport> {
  const outcomes: MovieOutcome[] = [];
  let index = 0;

  async function worker(): Promise<void> {
    while (index < ids.length) {
      const myIndex = index++;
      outcomes[myIndex] = await importMovie(ids[myIndex], deps);
    }
  }

  const workers = Array.from({ length: Math.max(1, Math.min(concurrency, ids.length)) }, () => worker());
  await Promise.all(workers);

  const imported = outcomes.filter((o) => o.status === 'imported').length;
  const skipped = outcomes.filter((o) => o.status === 'skipped').length;
  const failed = outcomes.filter((o) => o.status === 'failed').length;
  return { total: ids.length, imported, skipped, failed, outcomes };
}
