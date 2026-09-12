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
  both stay within PostgreSQL.
- Only if that proves insufficient, introduce a dedicated search engine and index
  asynchronously. The `SearchUseCases` boundary isolates callers from the change.

## Consequences
- No extra services to run, secure, or back up for the current scale.
- Substring `LIKE '%term%'` cannot use a plain B-tree index for the leading
  wildcard; acceptable now, with the trigram path ready if needed.
