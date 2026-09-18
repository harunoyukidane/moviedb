# V2.5: search scaling hardening

```yaml
status: current
canonical_for: v2.5-backlog
last_verified: 2026-09-18
```

Not started. Triggered by a search-scaling review against the stated target
in [product/requirements.md](../../product/requirements.md#L44-L48) — under
100,000 movies, 500,000 people, 100 concurrent users, search p95 under 750ms.

Both items here were already anticipated as an "evolution path, documented,
not built" in [ADR-8](../../decisions/0008-offset-pagination-keyset-evolution.md)
and [ADR-9](../../decisions/0009-defer-search-cache-messaging.md). This plan
scopes that evolution into concrete work; it does not change either decision.

## Status

| Item | Area | Status |
|---|---|---|
| [V2.5-01](#v25-01-trigram-index-for-substring-search) | `pg_trgm` GIN index for movie/person search | ☐ not started |
| [V2.5-02](#v25-02-push-search-offsetlimit-to-the-database) | Push search offset/limit to the database | ☐ not started |

## What the review found

- Movie search (`title`, `original_title`) and person search (`name`) both
  match with `lower(col) LIKE lower(:pattern) ESCAPE '\'` and a `%term%`
  pattern (substring) —
  [MovieRepository.kt:26-27](../../../backend/catalogue-service/src/main/kotlin/com/moviecatalogue/catalogue/movie/MovieRepository.kt),
  [PersonRepository.kt:26](../../../backend/people-service/src/main/kotlin/com/moviecatalogue/people/person/PersonRepository.kt).
- The existing functional B-tree indexes (`ix_movie_title_lower`,
  `ix_person_name_lower`) only accelerate *prefix* matches (`term%`). A
  leading wildcard gives a B-tree no fixed point to seek to, so `%term%`
  falls back to a full scan. At today's row counts that's noise; it stops
  being noise as the catalogue approaches the 100k/500k ceiling above.
- Unified search
  ([SearchUseCases.kt:68-70,127-142](../../../backend/catalogue-service/src/main/kotlin/com/moviecatalogue/catalogue/application/SearchUseCases.kt))
  and the People service's own search
  ([PeopleApplicationService.kt:82-104](../../../backend/people-service/src/main/kotlin/com/moviecatalogue/people/application/PeopleApplicationService.kt))
  both fetch `offset + limit` rows starting at position 0, then
  `.drop(offset).take(limit)` in application memory instead of pushing the
  offset to the database. Every page request re-fetches and discards its
  entire prefix — cost grows with `offset`, compounding the unindexed scan
  above.

## V2.5-01: trigram index for substring search

**Goal:** make `LIKE '%term%'` on movie title/original_title and person name
use an index instead of a scan.

**Steps:**
1. Enable `pg_trgm` and add a GIN trigram index per searched column, via new
   Flyway migrations:
   - catalogue-service — `V8__add_search_trigram_indexes.sql`:
     ```sql
     CREATE EXTENSION IF NOT EXISTS pg_trgm;
     CREATE INDEX ix_movie_title_trgm ON movie USING GIN (lower(title) gin_trgm_ops);
     CREATE INDEX ix_movie_original_title_trgm ON movie USING GIN (lower(original_title) gin_trgm_ops);
     ```
   - people-service — `V4__add_search_trigram_index.sql`:
     ```sql
     CREATE EXTENSION IF NOT EXISTS pg_trgm;
     CREATE INDEX ix_person_name_trgm ON person USING GIN (lower(name) gin_trgm_ops);
     ```
2. No application code changes needed — the queries already run
   `lower(col) LIKE lower(:pattern)`; the Postgres planner picks the new GIN
   index over a sequential scan once it exists and statistics are current
   (run `ANALYZE` after a bulk seed/import so the planner's row estimates
   reflect the loaded data).
3. Keep the existing B-tree `_lower` indexes — they still serve exact/prefix
   lookups elsewhere (e.g. TMDB import dedup, alphabet-jump pagination in
   `PersonRepository.countByNameLessThan`); this is additive, not a
   replacement.
4. Verify with `EXPLAIN ANALYZE` against a dataset seeded to the
   requirements.md ceiling (100k movies / 500k people): confirm a Bitmap
   Index Scan on the new GIN index for a `%term%` query, and extend the
   load test to assert search latency stays inside the 750ms budget at that
   volume.
5. Update the doc comments in `MovieRepository.kt` / `PersonRepository.kt`
   (currently reference only the `_lower` index) and the ADR-9 "Reinforced"
   section once shipped.

**Cost to weigh:** GIN trigram indexes are larger and more expensive to
maintain on write than the existing B-tree ones. Worth confirming against
this app's read-heavy, low-write access pattern (catalogue edits are
infrequent relative to reads) before assuming it's free.

## V2.5-02: push search offset/limit to the database

**Goal:** stop re-fetching and discarding an ever-growing prefix of results
on every paged request.

**Why it's not a one-line fix for unified search:** `SearchUseCases.search`
ranks movie hits (exact-prefix title > substring title > related-credit-only)
*after* merging two different sources — the Catalogue's own title search and
movies reached via people matched by the People service's gRPC search. That
merged ranking has to happen in memory once both sources are in hand; it
can't be expressed as one SQL query's `OFFSET`. Pushing the offset to each
per-source fetch is still worth doing, it just doesn't remove the in-memory
merge/rank step.

**Steps:**
1. `PeopleApplicationService.searchPeople` (both the blank-query "list all"
   path and the non-blank search path,
   [lines 82-104](../../../backend/people-service/src/main/kotlin/com/moviecatalogue/people/application/PeopleApplicationService.kt)):
   replace the `PageRequest.of(0, offset + limit)` + `.drop(offset).take(limit)`
   pattern with a real DB-pushed offset. `PageRequest.of(page, size)` can
   only express page-aligned offsets (`offset = page * size`), so an
   arbitrary offset needs either a custom `Pageable` whose `getOffset()`
   returns the exact value (Spring Data JPA's `SimpleJpaRepository` calls
   `setFirstResult(pageable.getOffset())` directly, so this works without
   forcing page alignment), or bypassing `Pageable` for this query and
   calling `EntityManager`/`TypedQuery.setFirstResult(offset).setMaxResults(limit)`
   explicitly in a custom repository method. Prefer the explicit
   `EntityManager` route — clearer than relying on `Pageable` internals.
   This also removes the `offset % limit == 0` fast-path special-casing
   currently needed as a workaround (lines 88-93) — it becomes unnecessary
   once arbitrary offsets are pushed to the DB directly.
2. `SearchUseCases.search` (catalogue): push the real offset+limit to
   `movies.searchByTitlePattern` for the movies-only path. For the unified
   (title + people-matched) path, a design call is needed:
   - **Option A (recommended to start):** push DB-side offset/limit
     whenever no people matched (`relatedByMovie` is empty), which is the
     majority case since most searches don't have a related-credit
     component; fall back to the current fetch-window + in-memory merge
     only when related-credit movies must be interleaved.
   - **Option B:** leave unified search as-is and only fix the two
     single-source paths (plain movie list/search, plain people search),
     since those are the ones with genuinely unbounded offset cost. Simpler,
     smaller change; revisit unified search only if it shows up in
     measurement.
3. Add/extend tests asserting a large offset does not fetch a
   proportionally large row set from the DB (e.g. assert on the SQL/`EXPLAIN`
   row estimate, or a repository-call assertion that the requested window
   size no longer grows with `offset`).
4. Update ADR-8's "Evidence" section once implemented.

## Sequencing

V2.5-01 (trigram index) is safe to ship independently and first — additive,
no behavior change. V2.5-02 (pagination) touches control flow and needs the
Option A/B call above before starting.
