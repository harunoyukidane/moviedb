# V2 catalogue: filters, seed, comments

```yaml
status: current
canonical_for: v2-catalogue-plan
last_verified: 2026-09-15
```

Status: not started. Corresponds to [requirements.md](../../product/requirements.md)
requirements 3 and 5.

## V2-05: Extend the movie-list GraphQL contract

- Add `MovieFilterInput { genreCode, releaseYear }` and an optional `filter` argument to `movies`.
- Validate genre codes against controlled reference data and validate a bounded four-digit release year.
- Add repository/application queries that combine genre and year with AND semantics.
- Preserve exact `total`, arbitrary offset behavior, stable ordering, and the 1–100 limit clamp.
- Avoid duplicate movie rows when filtering through `movie_genre`.
- Add repository and GraphQL integration tests for genre-only, year-only, combined, cleared, empty, and paginated results.

## V2-06: Add filter controls to `/movies`

- Load active genres and available/configured year choices.
- Store filter state in query parameters so results are linkable and browser navigation works.
- Reset `offset=0` whenever a filter changes; retain filters during pagination.
- Display the matching total and a specific no-results state.
- Update `$lib/server` types/operations and add component/route tests.

## V2-07: Expand deterministic demo content

- Add an append-only Flyway reference-data migration and matching importer mappings for a useful breadth of genres — at minimum Action, Comedy, Crime, Drama, Horror, Mystery, Romance, Thriller — retaining Psychological Horror as the existing editorial genre.
- Extend the committed TMDB ID manifest to cover the expanded genre set and several release years.
- Keep importer concurrency, mappings, retry behavior, and idempotency unchanged.
- Add assertions that the seeded set makes both filters demonstrable.
- Do not commit downloaded JSON or images; all image bytes go to MinIO through service endpoints.

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
