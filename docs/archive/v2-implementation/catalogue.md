# V2 catalogue: filters, seed, comments

```yaml
status: archived
canonical_for: none
last_verified: 2026-09-15
```

Status: 🟢 done — V2-05/V2-06/V2-07 (requirement 3) and V2-13/V2-14
(requirement 5, comments) all complete. Corresponds to
[requirements.md](../../../product/requirements.md) requirements 3 and 5.

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
multiple codes automatically. `demo/tmdb-movie-ids.txt` grew from 12 to 26
curated ids spanning 1949–2019 and every mapped genre; the original 12 ids
were kept, but a live import run against real TMDB (see below) turned up 8
stale title comments on that original set (e.g. id `609` is actually
"Poltergeist" (1982), not "Nosferatu") — those comments are now corrected
to verified titles/years/genres, the ids themselves are unchanged. Importer
concurrency/retry/idempotency logic (`importer.ts`, `tmdb.ts`) is unchanged —
only the mapping table and manifest content moved. Demonstrability is
covered by tests rather than a live TMDB run: a Catalogue schema integration
test asserts all nine genre codes are seeded; importer tests assert
`GENRE_MAP` covers every non-editorial genre, a multi-genre TMDB payload maps
to multiple codes, and the committed manifest parses cleanly with no
silently-dropped lines and stays above a minimum size. A live seed run was
also performed (host-side importer, `scripts/seed-host.ps1`, needed because
this environment's network TLS-intercepts api.themoviedb.org and the
in-container importer can't trust that proxy CA): all 26 movies imported
with posters, and the genre/year filters were confirmed against real data —
see [v2-acceptance.md](../../../verification/v2-acceptance.md). No downloaded
JSON/images are committed; posters and photos still go to MinIO through the
existing service endpoints at import time.

## V2-13: Comment persistence model — done

Added `V4__add_movie_comment.sql` (append-only; `V1`-`V3` untouched): `movie_comment`
with a UUIDv7 `id`, `movie_id` FK `ON DELETE CASCADE`, `author_display_name`
(`VARCHAR(50)`), `text` (`VARCHAR(2000)`), `created_at`, both non-blank via
`CHECK`, and `ix_movie_comment_movie_created` on
`(movie_id, created_at DESC, id DESC)`. `comment/MovieComment.kt` is a minimal
JPA mapping (no update path — comments have no edit/delete of their own, only
the movie's cascade delete removes them); `comment/CommentRepository.kt` adds
the single derived query `findAllByMovieIdOrderByCreatedAtDescIdDesc`.
`domain/Rules.kt` gained `MovieCommentRules` (trim + blank/length checks
mirroring the DB constraints, `AUTHOR_DISPLAY_NAME_MAX = 50`,
`TEXT_MAX = 2000`). Covered by `MovieCommentRulesTest` and by
`CatalogueRepositoryIntegrationTest` (persist, reverse-chronological
paging with an id tie-break, cascade delete removes comments).

## V2-14: Comment application and GraphQL API — done

Added `comments(movieId, page)` (query) and `addMovieComment(movieId, input)`
(mutation) to the schema, plus a new `DateTime` scalar (ISO-8601 offset,
`GraphQlScalarConfig`) for `MovieComment.createdAt`. `application/
CommentUseCases.kt` generates the id (UUIDv7) and `createdAt`
(`OffsetDateTime.now()`) itself — the comment entity's `created_at` column is
app-supplied rather than DB-default-only, since a JPA entity whose timestamp
is populated purely by a DB default isn't visible in memory for the mutation's
own response — and validates the movie exists before normalizing input via
`MovieCommentRules`, so a bad movie id fails before validation. Reads/writes
stay entirely inside Catalogue (no new service). `NotFoundException`/
`ValidationException` already map to `NOT_FOUND`/`BAD_USER_INPUT` via the
existing `GraphQlExceptionResolver`, so no new error-mapping code was needed.
Covered by `CommentUseCasesTest` (mockk) and
`CatalogueGraphQlIntegrationTest` (persist + trim, reverse-chronological
ordering and pagination, blank/over-limit input, missing movie on both query
and mutation, movie-delete cascade).
