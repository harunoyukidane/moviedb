# V1 implementation phases — historical

```yaml
status: archived
canonical_for: none
last_verified: 2026-09-15
```

These eight files were the phase-by-phase execution plan used to build v1; every
phase is complete (see [../../verification/v1-acceptance.md](../../verification/v1-acceptance.md)).
They are kept for historical reasoning only — do not load them as current
context, and do not treat "Status: ready to execute" in each file as still true.
Prefer `git log` for what actually happened and when.

## What each phase built

1. **Scaffolding & spec edits** — Gradle multi-project, contracts module,
   People/Catalogue service skeletons, Compose, CI skeleton.
2. **People Service (gRPC)** — domain rules, repository, application use cases,
   gRPC inbound adapter, readiness.
3. **Catalogue GraphQL layer** — GraphQL contract, batched credit hydration (no
   N+1), add-credit flow, person safe delete, stable error contract.
4. **Artwork (HTTP media)** — the `ArtworkStore` abstraction, `LocalArtworkStore`,
   upload validation, HTTP serving with caching, orphan sweeper (see ADR-4,
   later amended by ADR-14).
5. **Frontend BFF + UI CRUD** — SvelteKit server-side BFF pattern, routes for
   movies/people CRUD, movie editor behaviour, UI state rules.
6. **Search** — debounced unified search across movies and people, combined
   credit-match ranking.
7. **TMDB importer + setup scripts** — deterministic manifest-driven import,
   idempotent upserts, `setup.sh`/`reset-demo.sh`.
8. **Integration & polish** — README, `/about` + TMDB attribution, Playwright
   E2E, observability pass, security baseline pass, acceptance sign-off, ADRs
   finalized.

Full task-level detail for any phase is in `phase_N.md` in this directory.
