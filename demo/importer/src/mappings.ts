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

/**
 * TMDB's `place_of_birth` trailing country token -> the `name` stored in the
 * People service's `country_code` table (V3__add_country_reference_data.sql,
 * seeded with the ISO 3166-1 English short names). Only covers the common
 * free-text variants TMDB actually produces; anything else falls through to
 * an exact case-insensitive match against the country_code names themselves.
 * Never invent a country code - an unmatched token just leaves
 * birthCountryCode unset (see resolveBirthCountry in importer.ts).
 */
export const COUNTRY_NAME_ALIASES: Record<string, string> = {
  usa: 'United States',
  'u.s.a.': 'United States',
  'u.s.a': 'United States',
  'united states of america': 'United States',
  us: 'United States',
  uk: 'United Kingdom',
  'u.k.': 'United Kingdom',
  england: 'United Kingdom',
  scotland: 'United Kingdom',
  wales: 'United Kingdom',
  'northern ireland': 'United Kingdom',
  'south korea': 'South Korea',
  'korea, republic of': 'South Korea',
  'republic of korea': 'South Korea',
  russia: 'Russia',
  'russian federation': 'Russia',
  'czech republic': 'Czechia',
  'ivory coast': "Cote d'Ivoire",
  'the netherlands': 'Netherlands',
  'hong kong sar china': 'Hong Kong'
};
