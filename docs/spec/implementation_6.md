# Implementation Phase 6 — Search

Status: ready to execute
Depends on: phase 3 (GraphQL + People gRPC), phase 5 (UI shell + BFF)
Reference: TECHNICAL_SPECIFICATION v0.1.md §9 (search design), §8.1 (`search`, `SearchResult` types), §14 (escape wildcards)

## Objective

Implement one unified debounced search that finds movie titles and people, and also surfaces movies credited to matching people. Deliver base search first, then add the person->movie credited-relationship traversal.

## Server behaviour (§9)
`search(query, page)` within a bounded request:
1. Catalogue DB searches movie title / original title (case-insensitive, escaped wildcards).
2. People Service searches person names via gRPC (concurrently).
3. **(Second increment)** Catalogue DB finds movies with credits for the returned person IDs -> populate `MovieSearchHit.matchedPersonNames`.
4. Deduplicate and rank: exact prefix title/name, then substring title/name, then related-credit match.
- Validate min/max query length; clamp pagination; escape `%` and `_` so user input is literal (§14). Blank query handled consistently (rejected or treated as normal list — pick one and apply everywhere).

## Frontend behaviour (§9, §11.3)
- Single search box; debounce ~300 ms.
- Each request carries a query token; stale responses are cancelled/ignored so a newer query always wins.
- Loading/empty/results states; results show movies (with matched person names) and people.
- Runs through the BFF (server route), consistent with phase 5.

## Tasks (in order)
1. Movie-title search repository query (escaped, indexed via `ix_movie_title_lower`) + unit/integration tests.
2. `search` resolver: concurrent movie-title + People gRPC name search; dedup + ranking; base `SearchResult` shape.
3. Frontend search box with debounce + stale-token cancellation + result rendering + component tests.
4. **Person->movie traversal increment:** query `movie_credit` by returned person IDs (uses `ix_credit_person`), attach `matchedPersonNames`; extend tests.

## Test requirements
Unit/edge:
- Literal `%`, `_`, apostrophe, Unicode, mixed-case queries return correct, safe results (no wildcard injection).
- Blank/whitespace query handled per the chosen consistent rule.
- Min length below threshold rejected; max page size clamped; invalid offset handled.
- Ranking order: exact prefix before substring before related-credit.
- Person->movie: a query matching only a person's name surfaces that person's movies with `matchedPersonNames` populated.
Frontend:
- Debounce coalesces rapid keystrokes; stale response cannot overwrite a newer query's results (token guard).
- Loading/empty/results states render.

## Demo statement (phase acceptance)
- Typing in one search box returns matching movies and people, and movies credited to matching people, with newer queries always winning over stale responses.
- Wildcard/Unicode/edge inputs are handled safely; unit and component tests pass.

## ADR
- Reinforces ADR-9 (defer dedicated search engine): escaped `ILIKE`/`lower()` matching is sufficient at the stated scale; documented trigram evolution path if latency degrades.
