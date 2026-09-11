# Implementation Phase 8 — Integration & Polish

Status: ready to execute
Depends on: all prior phases
Reference: TECHNICAL_SPECIFICATION v0.1.md §12.5 (attribution), §13 (resilience), §14 (security), §15 (observability), §16 (tests), §19 (acceptance checklist); Technical Assessment submission requirements

## Objective

Turn a working stack into a submittable deliverable: complete documentation with AI-usage disclosure, one critical end-to-end journey, TMDB attribution, and observability/security passes, then verify against the acceptance checklist and rehearse the demo.

## Tasks

### 1. README (submission requirement)
- Setup and run instructions from a fresh clone (Docker Compose; one secret; WSL/Git Bash note for Windows).
- Architecture summary (two services, GraphQL BFF, gRPC internal, artwork over HTTP, per-service PostgreSQL).
- Assumptions and trade-offs (§3 decisions, ADRs, credit last-write-wins, UUIDv7, person-image hybrid).
- Commands: `./scripts/setup.sh`, `--skip-seed`, `./scripts/reset-demo.sh`, `./gradlew test`, frontend tests, E2E.
- **AI-usage disclosure** — how AI tools were used and how the candidate can explain generated code (explicit assessment requirement).

### 2. `/about` page + TMDB attribution (§12.5)
- Display approved TMDB logo, less prominent than the app brand.
- Required notice: "This product uses the TMDB API but is not endorsed or certified by TMDB."
- Architecture summary blurb.

### 3. E2E happy path (Playwright)
- One critical journey: create person -> create movie -> add credit -> upload artwork -> search -> remove credit / delete.
- Runs against the composed stack; green in CI (or documented if time-boxed).

### 4. Observability pass (§15)
- Structured JSON logs: timestamp, level, service, correlation ID, operation, entity ID, duration, outcome.
- Correlation ID propagates GraphQL HTTP header -> gRPC metadata (verify end-to-end).
- Micrometer counters/timers: GraphQL ops, gRPC calls, import outcomes, artwork failures, missing-person references.
- Liveness (process) and readiness (DB + required gRPC connectivity) confirmed.
- Never log artwork bytes, tokens, biographies, or full GraphQL payloads.

### 5. Security baseline pass (§14)
- Secrets only via env; `.env` git-ignored.
- DBs and People gRPC bound to internal Compose network; only web + needed Catalogue ports published.
- GraphQL query depth/complexity limits and pagination caps in place.
- Upload byte/type limits; generated storage paths; traversal + executable-SVG blocked.
- Standard browser security headers; narrow CORS origin (BFF origin only).
- Containers run as non-root where practical.
- No internal exception details/PII in responses or logs.

### 6. Acceptance checklist sign-off (§19)
Walk every §19 item and confirm with evidence:
- Fresh clone starts from README; honest empty-app path on missing token; idempotent reseed.
- Browser uses GraphQL (via BFF) + HTTP media for artwork bytes; Catalogue uses gRPC (not shared DB) for People.
- Separate credentials/schema/DB per service.
- Person removal from movie never deletes person; referenced-person delete rejected.
- Credits batch-hydrated (no N+1 gRPC).
- Controlled code tables; person roles derived from credits.
- Lists/search bounded and indexed; artwork validated-by-content, size-limited, stored outside Git.
- Optimistic locking prevents lost updates; expected GraphQL/gRPC errors stable and tested.
- Tests run against real PostgreSQL via Testcontainers; TMDB attribution present.
- README explains architecture, commands, assumptions, trade-offs, and AI usage.

### 7. CI completion + demo rehearsal
- CI runs: format/lint, frontend tests/build, backend unit + integration, protobuf/schema checks, container builds, Compose smoke test if time permits.
- Rehearse the reviewer flow end-to-end on a clean checkout.

### 8. ADRs finalized (§20)
- Ensure ADR-1..13 (incl. the four new: UUIDv7, BFF, person-image hybrid, credit last-write-wins) exist under `docs/decisions/` with context/decision/consequences.

## Test requirements
- Playwright E2E journey green.
- Full test suite (unit/integration/component/E2E) green in CI.
- Smoke test on composed stack (if time permits).

## Demo statement (phase acceptance)
- On a clean machine, an assessor can clone, set one secret, run `./scripts/setup.sh`, and complete the full critical journey in the browser.
- Every §19 acceptance item is satisfied with cited evidence.
- README (with AI-usage disclosure) and TMDB attribution are present; observability and security baselines verified.
