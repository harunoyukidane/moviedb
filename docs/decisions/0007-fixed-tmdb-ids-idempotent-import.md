# ADR-7: Fixed TMDB IDs and an idempotent setup-time import through application interfaces

Status: accepted

## Context
The demo needs a populated, browsable catalogue with posters, reproducibly, from a
public data source (TMDB) — without a runtime dependency on TMDB and without
bypassing the application's own validation and ownership rules.

## Decision
- Seed from a **committed manifest of fixed TMDB movie IDs**
  (`demo/tmdb-movie-ids.txt`, ~12 curated horror titles) so the catalogue is
  semantically stable across runs. Do not use the full daily ID export; do not
  commit downloaded JSON or images.
- Import **through the application's own interfaces**, never direct DB writes
  (§12.3): People gRPC (`CreatePerson`/`SearchPeople`), Catalogue GraphQL
  (`createMovie`/`addMovieCredit`), and the artwork HTTP endpoint (poster upload).
- Make every step **idempotent by external id**: people by `tmdb_id`, movies by
  `tmdb_id`, credits by TMDB credit id. Reruns update, never duplicate.
- Map remote codes to the controlled tables via version-controlled maps; never
  create uncontrolled code rows from arbitrary remote strings. The original crew
  job is preserved in `source_role_name`.
- After seeding, the app has **no runtime TMDB dependency**.

## Why the importer lives in phase 7, not phase 1
It calls interfaces built in phases 2–4 (People gRPC, Catalogue GraphQL, artwork
upload). Importing earlier would require throwaway direct-DB code that violates the
"import through application interfaces" rule.

## Evidence (phase 7)
- Importer: `demo/importer/` (TypeScript/Node). `TmdbClient` fetches
  `/3/movie/{id}?append_to_response=credits` with a Bearer token, bounded
  exponential backoff + jitter honoring `Retry-After` on 429/5xx, request
  deadlines, and deliberate error classification (401 fatal, 404 skip, transient
  retry, malformed handled). `importMovie` maps genres/crew to controlled codes,
  dedupes people by TMDB id, and upserts people → movie+genres → credits → poster.
- Idempotent upsert support added to the application interfaces: People gRPC
  `CreatePerson` already carries `tmdb_id`; Catalogue `CreateMovieInput` gained an
  optional `tmdbId` and `CreateCreditInput` gained `tmdbCreditId`/`sourceRoleName`
  (additive, non-breaking). The use cases upsert by these keys
  (`Movie.findByTmdbId`, `Credit.findByMovieIdAndTmdbCreditId`).
- Concurrency 4 worker pool; a failed item never rolls back prior successes.
- Tests (`vitest`, 24 total): rerun idempotency (identical counts), 401 stops
  without infinite retry, 404 records a skip and continues others, 429/5xx bounded
  retry honoring `Retry-After`, timeout/malformed handled without crashing, missing
  poster still imports, failed upload is non-fatal, partial credits handled,
  person dedup, genre/crew mapping (unmapped skipped).
- Scripts: `scripts/setup.sh` (validate Docker/Compose, `up -d --build --wait`,
  health-gated, one-shot `demo-importer` under the `seed` profile, prints
  URLs/counts/test commands, `--skip-seed`, empty-app fallback that never fakes
  success) and `scripts/reset-demo.sh` (removes only this project's named volumes,
  with a clear warning). Windows note: run under WSL or Git Bash.
- Secrets: `.env` is git-ignored; `.env.example` documents `TMDB_READ_TOKEN` with
  no value; tokens are passed only via env, never logged or committed.

## Consequences
- A fresh clone + token + `./scripts/setup.sh` yields a populated, browsable
  catalogue with posters; re-running is idempotent.
- Without a token (or with `--skip-seed`) the app comes up empty but usable, with
  honest seeding instructions.
- The importer is a language outlier (TypeScript vs. the Kotlin backend); it was
  chosen because it orchestrates gRPC + GraphQL + HTTP multipart cleanly and keeps
  its own tests independent of the JVM build.
