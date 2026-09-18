# V2.5-01 benchmark: trigram index, measured at the requirements.md ceiling

```yaml
status: current
canonical_for: v2.5-01-benchmark-results
last_verified: 2026-09-19
```

Real numbers, not the analytical estimate this plan started with. Run against
a throwaway, isolated Postgres 16 container (not the dev/demo databases) —
four databases seeded with identical, deterministic synthetic data: 100,000
movies and 500,000 people, matching the [requirements.md](../../../product/requirements.md#L44-L48)
ceiling. `catalogue_before`/`people_before` have the schema up to (not
including) the trigram migration; `catalogue_after`/`people_after` add it.
Scripts in this directory (`seed_movies.sql`, `seed_people.sql`,
`bench_*.sql`) reproduce the setup.

## Headline result

The determining factor is **search-term selectivity relative to the page
size, not movie vs. person**. Two distinct regimes:

| Regime | Before | After | What changes |
|---|---|---|---|
| Common term, small `LIMIT` | Already fast | Same | Postgres can walk the existing `_lower` B-tree in title/name order and stop at `LIMIT`, without ever needing the substring predicate to be indexed |
| Rare/specific term, or any `COUNT` (no `LIMIT` possible) | 200ms–2.5s, and gets *worse* under concurrent load | 0.6–6.9ms | No early exit is possible without an index that can actually filter on the substring |

Realistic user searches (an actual actor's name, a specific movie title
fragment) fall in the second regime far more often than a generic 3-letter
substring does — so this isn't an edge case. And the **`COUNT` query is
already in production** (`PersonRepository.countByNamePattern`, run by
`PeopleApplicationService.searchPeople` on every non-blank search) — it has
no `LIMIT` to exploit, so it's in the slow regime unconditionally today,
regardless of how common or rare the term is.

## Single-query cost (`EXPLAIN ANALYZE`, one connection)

| Query | Before | After | Speedup |
|---|---|---|---|
| Movie title search, common term (`%man%`, ~15% of rows), `LIMIT 20` | 3.0ms — B-tree walk + early exit | 1.3ms — same plan, trigram not chosen | ~parity |
| Movie title search, rare term (single-row match), `LIMIT 20` | 209.6ms — B-tree walk scans ~100k rows before finding the 1 match | 0.94ms — `BitmapOr` on the two trigram indexes | **~223x** |
| Person name search, `%man%` (rare for name data — no first/last name in the seed word list contains "man"), `LIMIT 20` | 377.7ms | 0.63ms | **~600x** |
| Person `countByNamePattern` (production query, no `LIMIT`), `%man%` | 204.9ms — parallel seq scan, all 500k rows examined | 1.4ms — `Bitmap Heap Scan` on `ix_person_name_trgm` | **~146x** |

## Concurrent load (`pgbench`, 20 clients / 4 threads / 10s — a slice of the
requirements' 100 concurrent users, at the DB connection-pool layer)

| Workload | Before: tps / avg latency | After: tps / avg latency | Speedup |
|---|---|---|---|
| Movie title search, common term | 1859 tps / 10.8ms | 1602 tps / 12.5ms | no meaningful difference (noise) |
| Movie title search, rare term (`:n` varies 1–100000 per call) | **12.1 tps / 1653ms** | 892.9 tps / 22.4ms | **~74x** |
| Person name search, `%man%` | **8.0 tps / 2508ms** | 2909 tps / 6.9ms | **~364x** |
| Person `countByNamePattern`, `%man%` | **8.4 tps / 2386ms** | 4651 tps / 4.3ms | **~555x** |

## This changes the risk assessment in ADR-9

The earlier write-up in [ADR-9](../../../decisions/0009-defer-search-cache-messaging.md#evolution-triggered-2026-09-18)
reasoned analytically that a single unindexed search was "plausibly
single-digit-to-low-tens of milliseconds" at this data volume, and that the
real risk was concurrency headroom rather than an already-measured breach.
The measured numbers say that reasoning was too optimistic for two reasons
it didn't account for:

1. **Contention makes it much worse than the single-connection number
   suggests**, even at only 20 concurrent connections (a fraction of the
   requirements' 100 users): the person count query goes from 205ms
   (one connection) to 2.4 **seconds** average (20 concurrent) — an ~11.6x
   degradation from contention alone on top of the base cost, not a linear
   20x-more-total-throughput story.
2. **The realistic query shape is the "rare term" one, not the "common
   term" one** a first EXPLAIN happened to test. A specific actor's name or
   a distinctive title fragment behaves like the rare-term case, and that
   case — plus the `COUNT` query, which is unconditionally in this regime
   — already blows the 750ms search budget by 2-3x under load that's well
   below the stated 100-concurrent-user ceiling, **before** the index.

So: this isn't "headroom for later," it's a real, already-reachable
regression at the documented target scale. V2.5-01 is not optional
future-proofing — treat it as a correctness-adjacent fix for the stated
performance requirement.

## Caveats

- Synthetic data, not real TMDB-shaped data: titles/names are built from
  small word lists (see `seed_movies.sql`/`seed_people.sql`), so real-world
  selectivity distributions will differ in degree, not in kind — the
  common-term/rare-term split is a real Postgres planning behavior, not an
  artifact of this data generator.
- Benchmarked at the Postgres layer directly (`pgbench` against the same SQL
  `MovieRepository`/`PersonRepository` issue), not through the full
  GraphQL/gRPC/HTTP stack — isolates the database cost, which is what the
  index change affects; app-layer overhead (serialization, gRPC hop for
  `SearchUseCases`, the in-memory pagination cost from V2.5-02) is additive
  on top of these numbers, not included in them.
- Run against an isolated, throwaway container sized like a typical dev
  machine, not the production-shaped hardware this would actually run on —
  absolute numbers will shift, the *before/after ratio* and the
  common-vs-rare-term regime split are the durable findings.
