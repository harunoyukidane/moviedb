# ADR-9: Defer dedicated cache/search/message infrastructure until measured need

Status: accepted

## Context
Search, caching, and messaging are easy to over-engineer. A dedicated search
engine (Elasticsearch/OpenSearch), a cache tier (Redis), or a message broker each
add operational weight. At the stated scale of this catalogue, PostgreSQL can
serve search directly.

## Decision
Do not introduce a dedicated search engine, cache, or message broker. Implement
unified search with escaped, case-insensitive `lower()`/`LIKE` matching in
PostgreSQL, backed by the existing functional indexes. Revisit only if measured
latency or relevance requirements justify the added infrastructure.

## Reinforced (phase 6)
Unified search is implemented entirely in PostgreSQL + gRPC, no new infrastructure:

- Movie titles: `MovieRepository.searchByTitlePattern` matches `lower(title)` /
  `lower(original_title)` with `LIKE ... ESCAPE '\'`, using `ix_movie_title_lower`.
  User-supplied `%`, `_`, and `\` are escaped (`SearchPattern`) so they match
  literally (§14) — verified by unit and integration tests.
- People: names are searched via the People Service gRPC `SearchPeople`
  (`ix_person_name_lower`), run concurrently with the title search.
- Person→movie: `CreditRepository.findAllByPersonIdIn` (uses `ix_credit_person`)
  surfaces movies credited to matched people, populating `matchedPersonNames`.
- Ranking is done in-process: exact-prefix title/name, then substring, then
  related-credit-only. Dedup by movie id. A People outage degrades to title-only
  results rather than failing (§13).

This meets the demo requirements with acceptable latency at the target scale.

## Evolution path (documented, not built)
If search latency or relevance degrades with data growth:
- Add a PostgreSQL **trigram** index (`pg_trgm`, `gin (lower(title) gin_trgm_ops)`)
  to accelerate substring matches, or `tsvector` full-text for ranked relevance —
  both stay within PostgreSQL. **Done — see "Evolution triggered" below.**
- Only if that proves insufficient, introduce a dedicated search engine and index
  asynchronously. The `SearchUseCases` boundary isolates callers from the change.
  Not needed: the trigram index measured as sufficient (see below).

## Consequences
- No extra services to run, secure, or back up for the current scale.
- Substring `LIKE '%term%'` cannot use a plain B-tree index for the leading
  wildcard. **No longer true for the indexed columns as of 2026-09-19** — the
  trigram index (V2.5-01) closes this gap; see "Evolution triggered" below.

## Evolution triggered (2026-09-18)

A search-scaling review against the requirements.md ceiling (100k movies,
500k people, search p95 under 750ms) found the substring-LIKE cost above is
no longer purely theoretical at that scale. The trigram index from the
evolution path is now scoped as concrete work in
[plans/v2.5/README.md](../plans/v2.5/README.md#v25-01-trigram-index-for-substring-search).

**Measured** (2026-09-19; see [plans/v2.5/benchmark/results.md](../plans/v2.5/benchmark/results.md)
for full methodology) against a dataset seeded to that exact ceiling — the
earlier analytical estimate below turned out to be too optimistic in two
ways it didn't account for. First, contention makes the unindexed cost much
worse than a single-connection number suggests: the production
`countByNamePattern` query goes from 205ms on one connection to **2.4
seconds average** under just 20 concurrent connections — a fraction of the
requirements' 100 users. Second, the realistic query shape (a specific
actor's name, a distinctive title fragment) behaves like the "rare term"
case, not the "common term" case a first check might reach for — and that
case, plus the `COUNT` query (which has no `LIMIT` to exploit and is already
in production), is unconditionally in the slow regime: 1.6–2.5 seconds
average latency under load, 2-3x over the 750ms search budget, **before**
the index. After the trigram index, the same workloads run in the
single-digit milliseconds. This is a real, already-reachable gap at the
documented target scale, not speculative future-proofing — the trigram index
is not "headroom for later," it fixes a measured breach of the stated
performance requirement. The pagination fix
([plans/v2.5/README.md#v25-02](../plans/v2.5/README.md#v25-02-push-search-offsetlimit-to-the-database))
remains the lower-priority item: it matters for deep-offset paging, which is
a real but rarer access pattern than the plain search this benchmark covers.

<details>
<summary>Original analytical estimate (2026-09-18), kept for context — see the measured numbers above instead</summary>

100k movies / 500k people is small enough (tens of MB) to likely sit
entirely in a warm Postgres cache, so a single unindexed scan is plausibly
single-digit-to-low-tens of milliseconds already — not, by itself, the thing
most likely to blow the 750ms budget. The bigger exposure is concurrency:
under the requirements' 100 concurrent users, every search paying the full
scan cost competes for the same CPU/IO, which shows up as p95 degradation
under load rather than a slow individual query in isolation. The trigram
index mainly buys headroom under that concurrent load (an order-of-magnitude
fewer rows examined per query) rather than fixing an already-measured
single-query breach.

</details>

## Hypothetical: search at 10M+ scale (out of scope)

Not a plan, not scoped, not needed at this project's stated target — kept
here only because the question ("what would IMDb-scale search require?")
came up during review and the answer is worth having on hand rather than
re-deriving. This is the natural continuation of the "Evolution path" above,
past the point where PostgreSQL (even with a trigram or `tsvector` index) is
still the right tool.

At tens of millions of titles/people, with high query volume and
sub-second/typo-tolerant/ranked expectations, the shift is from "a better
index inside Postgres" to dedicated search infrastructure:

- **Inverted index, not a scan.** A search engine (Elasticsearch/OpenSearch,
  built on Lucene) pre-builds a term → document-list index at write time, so
  a query becomes an index lookup rather than an examination of rows. This is
  the same principle as the `pg_trgm` GIN index above, generalized to
  full-text terms and built to scale horizontally.
- **Relevance ranking (BM25/TF-IDF)**, replacing the kind of fixed-tier
  ranking `SearchUseCases` does today (exact-prefix > substring >
  related-credit) — term-frequency/inverse-document-frequency scoring is
  needed once there are millions of loosely-relevant matches to order.
- **Separate n-gram / edge-n-gram fields** for autocomplete and typo
  tolerance (fuzzy/Levenshtein-distance queries), distinct from the main
  relevance-ranked full-text field — the production analog of the trigram
  index, purpose-built per query pattern instead of one generic mechanism.
- **Sharding and replicas.** The index is partitioned across nodes (by hash
  or range) so no single machine holds all of it or serves all queries, with
  replicas per shard for availability and read throughput.
- **Asynchronous indexing**, not synchronous writes. The source-of-truth
  database stays canonical; changes propagate to the search index
  asynchronously (CDC, a message queue, or a write-behind indexer) rather
  than being kept transactionally consistent with every write — a few
  seconds of search-freshness lag is an acceptable tradeoff at this scale.
- **A caching/CDN layer** in front for the hottest queries (popular
  titles/people), since real-world query distribution is long-tailed and
  caching the head absorbs a large fraction of traffic before it reaches the
  search cluster.

This is meaningfully more operational surface (a cluster to run, secure,
back up, and keep in sync with the source of truth) than this project's
stated scale justifies — consistent with this ADR's original decision to
defer dedicated search infrastructure "until measured need." If this
project's real target ever became IMDb-scale, that would warrant a new ADR
superseding this one, not an extension of it.
