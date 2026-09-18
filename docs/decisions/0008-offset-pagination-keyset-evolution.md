# ADR-8: Offset pagination for the stated scale, with a documented keyset evolution path

Status: accepted

## Context
Lists (movies, people) and search results need pagination. Offset pagination
(`LIMIT/OFFSET`) is simple, gives total counts and jump-to-page, and is well
understood. Keyset (cursor) pagination scales better for very large, deep result
sets but complicates the API and rules out arbitrary page jumps.

## Decision
Use offset pagination with a hard `limit` clamp of 1–100 and a non-negative
`offset`. Every list/search query returns `{ items, total, limit, offset }`.
Revisit keyset pagination only if data volume makes deep offsets slow.

## Evidence
- `MovieRules.clampLimit` clamps to 1–100 (default 20); `clampOffset` floors
  negatives. Applied uniformly by `MovieUseCases.listMovies`,
  `SearchUseCases.search`, and the People `SearchPeople` path.
- GraphQL `PageInput { limit, offset }` with `MoviePage`/`PersonPage` returning
  `total/limit/offset`; the frontend pager uses these.
- Tests assert the clamp (e.g. `movies(page:{limit:9999}) { limit }` returns 100)
  and pagination bounds.

## Evolution path (documented, not built)
If deep-offset latency degrades with growth, switch list/search to keyset
pagination: order by a stable key (e.g. `(lower(title), id)` for movies, the
UUIDv7 `id` for recency) and page with `WHERE (key) > (:lastKey)`. UUIDv7 keys
(ADR-10) are time-ordered, which makes recency cursors natural. The
`limit/offset` GraphQL shape can carry an opaque `cursor` additively without
breaking existing clients.

## Consequences
- Simple, predictable pagination with totals and page jumps now.
- Deep offsets scan-and-skip; acceptable at the target scale, with the keyset path
  ready if needed.

## Gap found (2026-09-18)

A search-scaling review found that `SearchUseCases.search` and
`PeopleApplicationService.searchPeople` don't fully deliver on this decision:
both fetch `offset + limit` rows from position 0 and slice with
`.drop(offset).take(limit)` in application memory, instead of pushing the
offset to the database as offset pagination is meant to. This is a gap in the
implementation, not a reason to move to keyset yet. Scoped as concrete work in
[plans/v2.5/README.md](../plans/v2.5/README.md#v25-02-push-search-offsetlimit-to-the-database).
