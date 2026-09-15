// Version-controlled mappings from TMDB codes/strings to the app's controlled
// GenreCode / CreditRoleCode tables (§12.3). Anything not mapped here is skipped
// rather than creating uncontrolled code rows from arbitrary remote strings.
//
// The seeded controlled codes (Flyway V2 + V3) are:
//   Genres: HORROR, PSYCHOLOGICAL_HORROR, ACTION, COMEDY, CRIME, DRAMA,
//           MYSTERY, ROMANCE, THRILLER
//   Roles:  ACTOR (CAST), DIRECTOR, WRITER, PRODUCER (CREW)
// The demo manifest is chosen to exercise these. Extend both the code tables and
// these maps together if you broaden the manifest.

/** TMDB numeric genre id -> internal GenreCode. Unmapped ids are ignored. */
export const GENRE_MAP: Record<number, string> = {
  27: 'HORROR',
  28: 'ACTION',
  35: 'COMEDY',
  80: 'CRIME',
  18: 'DRAMA',
  9648: 'MYSTERY',
  10749: 'ROMANCE',
  53: 'THRILLER'
  // PSYCHOLOGICAL_HORROR has no TMDB id and is assigned editorially.
};

/**
 * TMDB crew `job` string -> internal CreditRoleCode. The importer keeps only the
 * jobs listed in §12.3 step 2; the original job is preserved in `sourceRoleName`.
 * Unmapped jobs are skipped.
 */
export const CREW_JOB_MAP: Record<string, string> = {
  Director: 'DIRECTOR',
  Writer: 'WRITER',
  Screenplay: 'WRITER',
  Producer: 'PRODUCER'
  // "Director of Photography", "Editor", and "Original Music Composer" have no
  // controlled code yet and are skipped (never invent codes). Add a code to the
  // Flyway seed AND a mapping here together to include them.
};

/** The bounded set of crew jobs we consider at all (§12.3 step 2). */
export const CONSIDERED_CREW_JOBS = new Set<string>([
  'Director',
  'Writer',
  'Screenplay',
  'Producer',
  'Director of Photography',
  'Editor',
  'Original Music Composer'
]);

/** Cap on cast entries kept per movie (top-N by order). */
export const MAX_CAST = 12;
