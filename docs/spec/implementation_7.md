# Implementation Phase 7 — TMDB Importer + Setup Scripts

Status: ready to execute
Depends on: phases 2–4 (People gRPC, Catalogue GraphQL, artwork upload) — importer imports **through application interfaces**, so those must exist first
Reference: TECHNICAL_SPECIFICATION v0.1.md §12 (bootstrap), §13 (TMDB failure rows), §14 (secrets)

## Objective

Provide a deterministic, idempotent demo-data bootstrap. A committed manifest of stable TMDB movie IDs drives a per-movie import of details, selected credits, people, and one poster each — all imported through the application's own interfaces (People gRPC, Catalogue GraphQL, artwork upload path), never by direct DB writes. Provide the reviewer setup/reset scripts.

## Why importer is here, not phase 1
The importer calls People gRPC (create/upsert people), Catalogue GraphQL (upsert movies/genres/credits), and the artwork HTTP endpoint (poster upload). Those interfaces are built in phases 2–4, so importing earlier would require throwaway direct-DB code that violates the spec's "import through application interfaces" rule (§12.3).

## Commit (only) — §12.1
- `scripts/setup.sh`, `scripts/reset-demo.sh`
- `demo/importer/` implementation + container
- `demo/tmdb-movie-ids.txt` — ~10–15 stable numeric IDs
- config examples + explicit genre/crew-job mapping tables
- `.env.example` (documents `TMDB_READ_TOKEN`, no value)
Do **not** commit downloaded JSON or images. Do not use the full daily ID export.

## Import algorithm (§12.3) — per manifest ID
1. Fetch `/3/movie/{id}?append_to_response=credits` with `Authorization: Bearer $TMDB_READ_TOKEN`.
2. Keep bounded subset: top 12 cast by order + crew jobs Director, Writer/Screenplay, Producer, Director of Photography, Editor, Original Music Composer.
3. Map TMDB genre IDs and crew job names via version-controlled mappings to internal `GenreCode`/`CreditRoleCode`; never create uncontrolled code rows from arbitrary remote strings; preserve original job in `source_role_name` when useful.
4. Deduplicate people by TMDB person ID.
5. Upsert people through People gRPC ownership, `tmdb_id` as idempotency key; capture `profile_path` as provenance URL (person-image hybrid).
6. Upsert movie + mapped genres by `tmdb_id` (via GraphQL/application interface).
7. Upsert credits by TMDB credit ID (repeat runs update, not duplicate).
8. Build poster URL from config + `poster_path`, download one moderate-size poster, feed through the artwork-upload path.
9. Record per-item outcomes; exit non-zero if required items fail.

Concurrency 4; request deadlines; exponential backoff with jitter for transient 429/5xx; honor `Retry-After`. Stay conservative (TMDB ~40 req/s, can change).

## Setup scripts (§12.2, §12.4)
`scripts/setup.sh`:
1. Validate Docker/Compose + required config.
2. `docker compose up -d --build --wait`.
3. Wait on explicit health checks (not sleeps).
4. Run one-shot `demo-importer` Compose service.
5. Print URLs, demo record counts, and test commands.
- `--skip-seed` flag; if `TMDB_READ_TOKEN` absent, start an empty usable app and print exact seeding instructions — never fake success.
`scripts/reset-demo.sh`: target only this project's named Compose volumes; warn clearly that local demo data will be removed.
- **Windows:** document that scripts run under WSL or Git Bash (Docker-only run confirmed; no PowerShell port).

## Reproducibility & secrets (§12.4, §14)
- Fixed IDs -> semantically stable catalogue.
- Import resumable/idempotent per movie/person/credit; a failed item does not roll back prior successes.
- Secrets never in logs, history examples, images, or Git; `.env` git-ignored; `.env.example` has no value.
- App has no runtime TMDB dependency after seeding.

## Test requirements
- Rerun idempotency: second import produces the same record counts (no duplicates) — keyed by tmdb ids/credit ids.
- TMDB 401 -> stop with "invalid/missing token," no infinite retry.
- 404 (missing movie) -> record failure, continue others.
- 429/5xx -> bounded retry with jitter, honor `Retry-After`, report unresolved items.
- Timeout and malformed payload handled without crashing the run.
- Missing poster -> movie still imports; no artwork.
- Partial credits handled.

## Demo statement (phase acceptance)
- Fresh clone -> set `TMDB_READ_TOKEN` in `.env` -> `./scripts/setup.sh` brings up the stack and seeds a populated, browsable catalogue with posters.
- Re-running setup/import is idempotent (identical counts).
- `--skip-seed` (or absent token) yields a usable empty app with honest instructions.
- Failure-mode tests pass.

## ADR
- ADR-7 (fixed TMDB IDs + idempotent setup-time import through application interfaces) — record with evidence.
