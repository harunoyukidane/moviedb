# V2 catalogue: filters, seed, comments

```yaml
status: current
canonical_for: v2-catalogue-plan
last_verified: 2026-09-15
```

Status: 🟡 in progress — V2-05/V2-06/V2-07 done (requirement 3 complete);
V2-13/V2-14 (requirement 5, comments) remain. Corresponds to
[requirements.md](../../product/requirements.md) requirements 3 and 5.

## V2-05: Extend the movie-list GraphQL contract — done

Added `MovieFilterInput { genreCode, releaseYear }` and an optional `filter`
argument to `movies` (schema, `graphql/Inputs.kt`, `MovieController`).
`MovieRepository.findAllByFilter` combines genre and year with AND semantics
via an `EXISTS` subquery against `movie_genre` (never a join), so a movie
assigned to multiple matching genres is never duplicated; an explicit
`countQuery` keeps `total` exact. `MovieUseCases.listMovies` validates the
genre code against `GenreCodeRepository` (existence only — inactive genres
still filter correctly since older movies may carry them) and the release
year against a bounded four-digit range (`MovieRules.RELEASE_YEAR_MIN`/`MAX`,
1888–2100) before querying; both map to `BAD_USER_INPUT`. Stable title
ordering, arbitrary offset, and the 1–100 limit clamp are unchanged. Covered
by repository integration tests (genre-only, year-only, combined, cleared,
empty, paginated, dedup) and GraphQL integration tests (combined filter,
total reporting, unknown genre code, out-of-range year).

## V2-06: Add filter controls to `/movies` — done

`movies/+page.server.ts` loads active genres via `listGenres` and a configured
release-year range (1900–current year + 1, generated in the load function
rather than queried, since there is no backend distinct-years aggregate) and
passes both to a new `lib/features/movies/MovieFilters.svelte` component.
Filter state lives entirely in `genreCode`/`releaseYear` query params — a
plain `method="GET"` form with no `offset` field, so submitting a filter
change always lands on `offset=0`, and pagination links
(`pagerHref` in `+page.svelte`) always carry the current filter params
forward. `+page.server.ts` only accepts a `genreCode` that matches a
currently loaded genre and a `releaseYear` that is an integer in range,
silently dropping anything else rather than erroring on a stale/tampered
query string. The listing shows the filtered `total` (unchanged pager UI)
and a filter-specific empty-state message distinct from the "no movies yet"
one. `$lib/server/operations.ts#listMovies` gained an optional
`MovieFilterInput` parameter. Covered by `MovieFilters.test.ts` (rendering,
preselection, clear-filters link) and `movies/page.server.test.ts`
(valid/invalid/combined filters, unknown genre and out-of-range year
ignored, offset retained across pagination, degraded genre-list fallback).

## V2-07: Expand deterministic demo content — done

Added `V3__expand_genre_reference_data.sql` (append-only; `V2` untouched) with
seven new controlled genres — Action, Comedy, Crime, Drama, Mystery, Romance,
Thriller — alongside the existing Horror and the editorial Psychological
Horror. `demo/importer/src/mappings.ts#GENRE_MAP` gained the matching TMDB
genre-id mappings (28/35/80/18/9648/10749/53); `mapGenres` already assigns
every matching id a movie carries, so a multi-genre TMDB movie now gets
multiple codes automatically. `demo/tmdb-movie-ids.txt` grew from 12
(Horror-only) to 26 curated, TMDB-verified ids spanning 1922–2019 and every
mapped genre, keeping the original Horror set untouched. Importer
concurrency/retry/idempotency logic (`importer.ts`, `tmdb.ts`) is unchanged —
only the mapping table and manifest content moved. Demonstrability is
covered by tests rather than a live TMDB run: a Catalogue schema integration
test asserts all nine genre codes are seeded; importer tests assert
`GENRE_MAP` covers every non-editorial genre, a multi-genre TMDB payload maps
to multiple codes, and the committed manifest parses cleanly with no
silently-dropped lines and stays above a minimum size. No downloaded
JSON/images are committed; posters and photos still go to MinIO through the
existing service endpoints at import time.

## V2-13: Comment persistence model

- Add an append-only Catalogue Flyway migration for `movie_comment` (UUIDv7 `id`, `movie_id` FK `ON DELETE CASCADE`, `author_display_name`, `text`, `created_at`; index on `(movie_id, created_at DESC, id DESC)`).
- Required trimmed author display name ≤50 Unicode characters; comment text ≤2,000 characters.
- Comments are physically deleted with their movie; standalone comment edit/delete is not in v2 scope.
- Add entity, repository, rules, and PostgreSQL integration tests.

## V2-14: Comment application and GraphQL API

- Add `comments(movieId, page)` and `addMovieComment(movieId, input)` to the GraphQL contract.
- Use server-generated UUIDv7 and timestamps; never trust a client timestamp.
- Return reverse chronological pages with stable ID tie-breaking and bounded limits.
- Validate that the movie exists and map failures through stable GraphQL error codes.
- Keep comment reads/writes inside Catalogue; do not introduce another service.
- Add application and GraphQL integration tests for empty, whitespace, over-limit, missing movie, ordering, pagination, and movie cascade deletion.
