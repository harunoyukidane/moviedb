# V2.5: search scaling hardening

```yaml
status: current
canonical_for: v2.5-backlog
last_verified: 2026-09-19
```

V2.5-01 done and benchmark-verified; V2.5-02 not started. Triggered by a
search-scaling review against the stated target
in [product/requirements.md](../../product/requirements.md#L44-L48) — under
100,000 movies, 500,000 people, 100 concurrent users, search p95 under 750ms.

Both items here were already anticipated as an "evolution path, documented,
not built" in [ADR-8](../../decisions/0008-offset-pagination-keyset-evolution.md)
and [ADR-9](../../decisions/0009-defer-search-cache-messaging.md). This plan
scopes that evolution into concrete work; it does not change either decision.

## Status

| Item | Area | Status |
|---|---|---|
| [V2.5-01](#v25-01-trigram-index-for-substring-search) | `pg_trgm` GIN index for movie/person search | ✅ done — index, tests, and benchmark all verified |
| [V2.5-02](#v25-02-push-search-offsetlimit-to-the-database) | Push search offset/limit to the database | ✅ done for `PeopleApplicationService.searchPeople` (Option B); unified search left as-is |

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
   - catalogue-service — `V9__add_search_trigram_indexes.sql` (V8 was taken
     by `V8__movie_title_icu_index.sql`, landed independently):
     ```sql
     CREATE EXTENSION IF NOT EXISTS pg_trgm;
     CREATE INDEX ix_movie_title_trgm ON movie USING GIN (lower(title) gin_trgm_ops);
     CREATE INDEX ix_movie_original_title_trgm ON movie USING GIN (lower(original_title) gin_trgm_ops);
     ```
   - people-service — `V5__add_search_trigram_index.sql` (V4 was taken by
     `V4__person_name_icu_index.sql`, landed independently):
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

**Done — all steps.** Migrations
([`V9__add_search_trigram_indexes.sql`](../../../backend/catalogue-service/src/main/resources/db/migration/V9__add_search_trigram_indexes.sql),
[`V5__add_search_trigram_index.sql`](../../../backend/people-service/src/main/resources/db/migration/V5__add_search_trigram_index.sql)),
repository doc comments, a regression test in each service
(`CatalogueRepositoryIntegrationTest`, `PeopleSearchIntegrationTest`)
confirming the index is structurally usable, and — step 4 — a full
before/after benchmark at the requirements.md ceiling (100k movies, 500k
people) with both `EXPLAIN ANALYZE` and concurrent-load (`pgbench`) numbers.
See [benchmark/results.md](benchmark/results.md) for the full write-up.
Headline: common-term searches were already fine (the existing B-tree lets
Postgres walk in sorted order and stop at `LIMIT`); rare/specific terms and
the production `countByNamePattern` query were not — up to **~555x** slower
without the index under just 20 concurrent connections (well under the
requirements' 100-user ceiling), with average latency reaching 1.6–2.5
**seconds**, 2-3x over the 750ms search budget. This was a real, already
reachable gap at the documented target scale, not speculative
future-proofing — see the ADR-9 update below.

**Cost to weigh:** GIN trigram indexes are larger and more expensive to
maintain on write than the existing B-tree ones. Worth confirming against
this app's read-heavy, low-write access pattern (catalogue edits are
infrequent relative to reads) before assuming it's free.

## V2.5-02: push search offset/limit to the database

**Goal:** stop re-fetching and discarding an ever-growing prefix of results
on every paged request.

**Measured impact (2026-09-19), before implementing:** unlike V2.5-01, this
is a moderate win, not a dramatic one — see
[benchmark/results.md](benchmark/results.md#option-b-pagination-pushdown-measured-2026-09-19)
for the full numbers. Postgres's `OFFSET` is still O(offset) internally (it
scans and discards the skipped rows itself — there's no index that lets it
seek straight to row 20,000), so the database-side compute cost barely
changes: ~1.3–1.7x at offsets from 5,000–20,000, single connection. The real
saving is downstream of the query — network transfer and JPA entity
hydration for `offset` rows the app was going to throw away anyway — which
is why it shows up more under concurrent load (transfer competes for
resources across connections): **~1.6x at offset 1,000, ~2.1x at offset
20,000**, 20 concurrent connections. Worth doing, priced correctly as a
moderate optimization rather than a correctness-adjacent fix like V2.5-01.

**Why it's not a one-line fix for unified search:** `SearchUseCases.search`
ranks movie hits (exact-prefix title > substring title > related-credit-only)
*after* merging two different sources — the Catalogue's own title search and
movies reached via people matched by the People service's gRPC search. That
merged ranking has to happen in memory once both sources are in hand; it
can't be expressed as one SQL query's `OFFSET`. Pushing the offset to each
per-source fetch is still worth doing, it just doesn't remove the in-memory
merge/rank step. This is why the plan considered two options:

- **Option A:** push DB-side offset/limit whenever no people matched
  (`relatedByMovie` is empty), falling back to the current fetch-window +
  in-memory merge only when related-credit movies must be interleaved.
  Needs rank computed in SQL (a `CASE WHEN` prefix-match expression in
  `ORDER BY`) plus a new count query to know whether a page is safely
  within the title-only tier before trusting a DB-side page — real added
  complexity for a moderate win, so **not done**.
- **Option B:** leave unified search as-is and fix only the two
  single-source paths (plain people list/search — the plain movie list
  already used this pattern via `OffsetPageRequest`). Simpler, no
  correctness-critical branch. **Done — see below.**

**Done — `PeopleApplicationService.searchPeople` (Option B):**
1. Ported catalogue-service's `OffsetPageRequest` (already proven there, in
   `MovieUseCases.listMovies`) to people-service as
   [`com.moviecatalogue.people.common.OffsetPageRequest`](../../../backend/people-service/src/main/kotlin/com/moviecatalogue/people/common/OffsetPageRequest.kt) —
   a custom `Pageable` whose `getOffset()` returns an arbitrary (not
   page-aligned) value; `SimpleJpaRepository` calls `setFirstResult`/
   `setMaxResults` directly from it. (Duplicated rather than shared: the two
   services don't share code, per ADR-1.)
2. Both branches of
   [`PeopleApplicationService.searchPeople`](../../../backend/people-service/src/main/kotlin/com/moviecatalogue/people/application/PeopleApplicationService.kt)
   (blank-query "list all" and the name-search path) now call
   `OffsetPageRequest(limit, offset.toLong())` instead of
   `PageRequest.of(0, offset + limit)` + `.drop(offset).take(limit)`. Passed
   **unsorted** — both `findAllOrderByName` and `searchByNamePattern` are
   native queries with their own ICU-collated `ORDER BY` baked in (V2.4); a
   sorted `Pageable` would append a second, conflicting `ORDER BY` — exactly
   the bug the "Alphabet pagenation" commit's own
   `movie filter combines genre and year...` test currently hits against
   `MovieRepository.findAllByFilter` (`ERROR: syntax error at or near
   "order"`, still failing as of 2026-09-19, unrelated to this change and
   not fixed here since it's someone else's in-progress work).
3. Tests: ported `OffsetPageRequestTest` to people-service, and added two
   integration tests (`PeopleSearchIntegrationTest`) asserting a
   **non-page-aligned** offset (7, with limit 3) returns the exact correct
   slice for both branches — the case the old `drop/take` workaround existed
   specifically to handle, now handled by the DB instead.
4. `SearchUseCases` (catalogue's unified top-bar search) is **untouched** —
   still does its own fetch-window + in-memory rank/merge/slice, per Option
   B. `movies.searchByTitlePattern` itself was already left alone too, since
   its only caller is the unified search path.

**Not done:** ADR-8's "Evidence" section update, and Option A (unified
search pushdown) — deferred per the measured-impact note above; revisit only
if unified search's deep-offset cost shows up in real usage.

## Sequencing

Both items are now done. V2.5-01 shipped first (additive, no behavior
change); V2.5-02 followed once the Option A/B call was made (Option B,
scoped down after the pagination benchmark showed a moderate rather than
dramatic gain).
