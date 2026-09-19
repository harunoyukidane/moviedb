# MovieDB

A small, production-shaped movie catalogue: browse, create, and edit movies and
people, manage cast/crew credits, upload artwork, and search — built as two
independent services behind a server-side BFF, with a reproducible TMDB-seeded
demo.

---

## Copyright

Copyright © 2026 Harunoyukidane. All rights reserved.

This repository is made available for portfolio viewing only. No permission is
granted to use, copy, modify, or distribute its contents.

---

## Quick start (fresh clone)

Prerequisites: **Docker** + **Docker Compose v2**, with Docker Desktop running.
Everything runs locally in Docker containers.

```bash
# 1. configure the one secret (optional but needed to seed demo data)
cp .env.example .env
#   edit .env and set TMDB_READ_TOKEN=<your TMDB v4 Read Access Token>
#   (create one at https://www.themoviedb.org/settings/api)

# 2a. bring up the stack and seed a browsable catalogue
#     Linux / macOS / WSL / Git Bash:
./scripts/setup.sh
#     Windows PowerShell:
./scripts/setup.ps1
```

`setup` validates Docker, builds and starts the services **waiting on health
checks** (not sleeps), runs the one-shot importer, and prints the URLs, demo record
counts, and example queries. The `.sh` and `.ps1` scripts are equivalent — use
whichever fits your shell.

- **No token?** The app still comes up **empty but fully usable**, and the script
  prints exact instructions to seed later. It never fakes success.
- **Skip seeding:** `./scripts/setup.sh --skip-seed` (bash) or
  `./scripts/setup.ps1 -SkipSeed` (PowerShell).
- **Reset (removes only this project's volumes):** `./scripts/reset-demo.sh` or
  `./scripts/reset-demo.ps1`.

### Seeding modes: normal vs. Cloudflare/WARP

The importer needs internet access (to TMDB). There are two modes:

- **Normal** (default): the importer runs as a one-shot container. Use this when
  Docker containers can reach the internet.
  ```powershell
  ./scripts/setup.ps1            # PowerShell
  ./scripts/setup.sh             # bash
  ```
- **Cloudflare / WARP / VPN**: if a TLS-intercepting proxy (e.g. Cloudflare WARP)
  tunnels only the host and not the Docker VM, the in-container importer can't
  reach TMDB. Run the importer **on the host** instead — it publishes People's gRPC
  to localhost, trusts the intercepting proxy's root CA, and seeds from the host:
  ```powershell
  ./scripts/setup.ps1 -Cloudflare   # or, if the stack is already up:
  ./scripts/seed-host.ps1
  ```
  This path is verified to import all demo movies **with posters**.

Then open the **browser UI** at `http://localhost:4173` (the SvelteKit BFF runs as a
container in the stack), or the Catalogue GraphQL API at
`http://localhost:8080/graphql`. `setup` builds the frontend on the host and runs
it as the `frontend` service, so the whole stack — UI included — comes up together.

To run the UI outside Docker instead (e.g. for frontend dev), start the BFF locally
(`cd frontend && npm install && npm run build && npm run preview`) pointed at the
Catalogue (`CATALOGUE_GRAPHQL_URL`, `CATALOGUE_HTTP_URL`, `PEOPLE_HTTP_URL`).

---

## Load / concurrency stress test

`demo/loadtest/` runs many concurrent workers against the running stack to
exercise write contention that a normal demo never hits: several workers
create/update/delete/upload artwork on the **same** small pool of movies and
people at once (so amends race deletes, deletes race deletes, amends race
amends), and a separate pool of workers comment on the **same** movie
concurrently.

```bash
docker compose --profile loadtest run --rm --build load-test
```

- All records it writes are synthetic and prefixed `[LOADTEST]`, and are
  removed again (best-effort) when the run ends — the curated demo catalogue
  is never touched.
- Tunable via env vars (defaults shown): `LOAD_DURATION_SECONDS=30`,
  `LOAD_MOVIE_WORKERS=20`, `LOAD_PEOPLE_WORKERS=10`, `LOAD_COMMENT_WORKERS=20`,
  `LOAD_HOT_MOVIES=6`, `LOAD_HOT_PEOPLE=6` — set them in `.env` or inline
  (`LOAD_DURATION_SECONDS=60 docker compose --profile loadtest run --rm load-test`).
- The report at the end separates **expected** races (`CONFLICT` from a stale
  optimistic-lock version, `NOT_FOUND` from amending something another worker
  just deleted) from **unexpected** errors (5xx, timeouts, anything without a
  recognised error code) — only the latter indicates a real bug.
- Running it against this codebase found and fixed exactly that kind of bug —
  see [docs/verification/loadtest-findings.md](docs/verification/loadtest-findings.md).

---

## Architecture

Two independently deployable services, each owning its own PostgreSQL database:

```
                 ┌───────────────────────────┐
  Browser ─────► │ Caddy edge proxy (proxy/)  │   (zstd/gzip; only published port)
                 └────────────┬──────────────┘
                              │ HTTP (internal network)
                              ▼
                 ┌───────────────────────────┐
                 │ SvelteKit BFF (frontend/)  │   (server-side only GraphQL)
                 └────────────┬──────────────┘
                              │ GraphQL (HTTP)          │ artwork bytes (HTTP)
                              ▼                         ▼
                 ┌─────────────────────────────────────────────────┐
                 │ Catalogue Service (GraphQL + media HTTP)         │  Postgres (catalogue)
                 │ movies · credits · genres · languages · artwork  │
                 └──────────────────────┬──────────────────────────┘
                                 │ gRPC (internal network)
                                 ▼
                 ┌───────────────────────────┐
                 │ People Service (gRPC)      │              Postgres (people)
                 │  people                    │
                 └───────────────────────────┘
```

- **Catalogue Service** (Kotlin / Spring Boot / Spring for GraphQL) owns movies,
  credits, genres, languages, and artwork metadata. Public API is **GraphQL**; artwork bytes
  use small dedicated **HTTP media endpoints**.
- **People Service** (Kotlin / Spring Boot / gRPC) is the authoritative owner of
  people, exposed only over **internal gRPC**.
- **Frontend** (SvelteKit / TypeScript) is a **server-side BFF**: the browser calls
  SvelteKit server routes, which call GraphQL server-side. The browser never calls
  GraphQL directly; there is no browser-to-backend CORS.
- **Edge proxy** (Caddy, ADR-15) is the only published entry point. It compresses
  every response — including the SSR'd HTML document, which `adapter-node` streams
  out uncompressed — and forwards to the BFF over the internal network. The BFF
  container is no longer published to the host; `localhost:4173` is the proxy.
- **Importer** (`demo/importer/`, TypeScript) seeds demo data through the
  application's own interfaces (People gRPC, Catalogue GraphQL, artwork HTTP) —
  never by direct DB writes.
- **Shared `media` module** provides the validated `ArtworkStore` reused by both
  movie artwork and person photos.

Databases and the People gRPC port bind to the internal Compose network; only the
Catalogue HTTP port is published.

In more depth: [overview.md](docs/architecture/overview.md) for boundaries, quality
priorities and the layering rules; [code-graph.md](docs/architecture/code-graph.md)
for the **measured** import graph — what actually depends on what, extracted from the
source rather than asserted; [frameworks.md](docs/architecture/frameworks.md) for
versions and the convention each framework imposes; and
[data-model.md](docs/architecture/data-model.md) for ownership, schema and
transaction boundaries.

---

## Repository layout

```
backend/               Gradle multi-project (Kotlin)
  contracts/           protobuf/gRPC contract (people.proto)
  media/               shared ArtworkStore + image content validator
  catalogue-service/   GraphQL API, credits, artwork, search
  people-service/      internal gRPC people service
frontend/              SvelteKit BFF + UI (TypeScript)
proxy/                 Caddy edge reverse proxy (compression; published entry point)
demo/importer/         TMDB importer (TypeScript)
demo/loadtest/         concurrency/stress load test (TypeScript)
demo/tmdb-movie-ids.txt  committed manifest of stable TMDB movie ids
scripts/               setup.sh, reset-demo.sh, build-test-specs.py
docs/                  Docs — start at docs/README.md for what to load
docs/architecture/     overview, code map, measured import graph, data model, frameworks
docs/decisions/        Architecture Decision Records (ADR-1..15)
docs/verification/     acceptance evidence + test-specs.xlsx (every test, one row each)
compose.yaml           full topology (+ seed profile for the importer, loadtest profile for the stress test)
```

---

## Object storage (MinIO)

Movie artwork and person photos are stored in MinIO (ADR-14), not on service-local
volumes. Compose runs one MinIO deployment with two buckets — `catalogue-artwork`
and `people-photos` — each accessible only via its own least-privilege identity;
a one-shot `minio-init` service provisions both buckets/identities/policies on
`docker compose up` and is safe to rerun.

- All uploaded images live in the `minio-data` named volume. `./scripts/reset-demo.sh`
  (or `.ps1`) removes it along with the databases — reseed afterwards via
  `./scripts/setup.sh`.
- Recreating `catalogue-service`/`people-service` containers (e.g.
  `docker compose restart`) no longer touches media bytes — they live in MinIO,
  decoupled from the application containers.
- Root/admin MinIO credentials are bootstrap-only (used by `minio-init`) and are
  never given to either application service.

---

## Commands

Backend (from `backend/`):
```bash
./gradlew test          # unit + integration (Testcontainers) for all modules
./gradlew build         # compile, test, package
```

Frontend (from `frontend/`):
```bash
npm install
npm test                # Vitest component/unit tests
npm run check           # svelte-check (type + a11y)
npm run build           # production build (also proves no browser-side GraphQL)
npm run test:e2e        # Playwright E2E (needs the stack running; see below)
```

Importer (from `demo/importer/`):
```bash
npm install && npm test # Vitest: retry/idempotency/failure-mode tests
```

Demo lifecycle (repo root):
```bash
# bash (Linux / macOS / WSL / Git Bash)
./scripts/setup.sh              # up + seed
./scripts/setup.sh --skip-seed  # up, empty app
./scripts/reset-demo.sh         # remove this project's volumes
```
```powershell
# Windows PowerShell (equivalent)
./scripts/setup.ps1
./scripts/setup.ps1 -SkipSeed
./scripts/reset-demo.ps1
```

End-to-end (Playwright): start the stack (`./scripts/setup.sh`) and the BFF
(`npm run build && npm run preview` in `frontend/`), then
`BASE_URL=http://localhost:4173 npm run test:e2e`. The journey covers create person
→ create movie → add credit → upload artwork → search → remove credit → delete movie
→ delete person; two further specs cover long-word layout containment and the BFF's
security headers.

> Note on this environment: backend tests use Testcontainers. On Docker Desktop /
> Windows they require pointing the JVM at a modern Docker API version — this repo
> sets `api.version` via `~/.docker-java.properties` (see the docker-java note) or
> passes `-PdockerApiVersion=1.44`. On Linux CI the default socket works unchanged.

### What those commands cover

Every automated test in the repository is itemized in
[docs/verification/test-specs.xlsx](docs/verification/test-specs.xlsx) — one row each,
with the module under test, steps, expected result, and the status read from that
runner's own report rather than assumed. That is **699 tests, all passing**: 378
backend (JUnit), 279 frontend (Vitest), 39 importer (Vitest) and 3 end-to-end
(Playwright), **383 of them edge cases** — boundaries, error paths, missing data,
degraded fallbacks. The workbook is generated by `scripts/build-test-specs.py` from
the test sources and the runner reports, so it is regenerated rather than
hand-edited; see [docs/verification/README.md](docs/verification/README.md) for the
strategy, the edge-case checklist, and the rebuild command.

---

## Assumptions and trade-offs

The significant decisions are recorded as ADRs in [`docs/decisions/`](docs/decisions/README.md).
Highlights:

- **Two services split by data ownership** (ADR-1): People owns people; Catalogue
  owns everything else and references people by id over gRPC. No shared database.
- **GraphQL externally, gRPC internally** (ADR-3); artwork bytes over HTTP media
  endpoints, not GraphQL (ADR-4).
- **UUIDv7 primary keys** generated in the app (ADR-10) for time-ordered,
  index-friendly keys; `tmdb_id` is nullable provenance, never the PK.
- **SvelteKit server-side BFF** (ADR-11): no browser-origin GraphQL, correlation
  IDs propagate server-side into gRPC.
- **Credit last-write-wins** (ADR-13): movies and people use optimistic locking
  (`expectedVersion`); credits are small and subordinate, so `updateMovieCredit`
  intentionally has no `expectedVersion`.
- **Person images** (ADR-12, amended by ADR-14): person photos go through the
  same validated `ArtworkStore` path as movie posters, now backed by MinIO; the
  importer downloads each person's TMDB image at seed time, so there is no
  runtime TMDB dependency.
- **Physical deletion with reference protection** (ADR-6): deleting a person who is
  still credited is rejected (`PERSON_IN_USE`); removing a person from a movie is a
  credit removal, never a person delete.
- **Controlled code tables** for genres/roles/languages (ADR-5); the importer never
  invents codes from arbitrary remote strings.
- **Offset pagination** clamped to 1–100 (ADR-8) with a documented keyset path.
- **No dedicated search engine** (ADR-9): escaped `lower()`/`LIKE` on indexed
  columns suffices at this scale; trigram/tsvector is the documented evolution.

---

## AI-usage disclosure

This project was built with substantial help from an AI coding assistant, used as a
pair-programmer under human direction. Specifically:

- **What the AI did:** scaffolded the Gradle/SvelteKit/importer projects; drafted
  Kotlin services (domain rules, JPA mappings, use cases, gRPC/GraphQL adapters),
  the SvelteKit BFF and components, the TMDB importer, tests across all tiers
  (unit, Testcontainers integration, in-process gRPC contract, Vitest component,
  Playwright E2E), the ADRs, the Compose topology, and these docs. It also
  diagnosed and fixed environment issues (e.g. the Docker Desktop / docker-java API
  mismatch) and iterated until builds and tests were green.
- **How it was directed:** work proceeded phase by phase against a written technical
  specification and per-phase implementation plans (archived under
  `docs/archive/v1-implementation-phases/`). Each phase was reviewed, built, and
  tested before moving on; failing tests and design gaps were fed back for
  correction.
- **How generated code can be explained:** every non-trivial decision is captured
  in an ADR with context/consequences, and the code is commented at the points
  where a rule or trade-off matters (e.g. batched hydration, compensation ordering,
  wildcard escaping, correlation-ID propagation). The author can walk through the
  service boundaries, the error contract (`extensions.code`), the optimistic-locking
  and safe-delete flows, the idempotent import, and the test strategy, and explain
  why each was chosen. Where behavior is subtle (e.g. last-write-wins for credits,
  degraded person hydration), the reasoning is documented in both code and ADRs.
- **Verification:** all code is covered by automated tests that were executed, not
  assumed — 699 of them, itemized with their last recorded result in
  [docs/verification/test-specs.xlsx](docs/verification/test-specs.xlsx); the build
  and test commands above reproduce them.

---

## Attribution

This product uses the TMDB API but is not endorsed or certified by TMDB. Movie and
person data and posters are sourced from
[The Movie Database (TMDB)](https://www.themoviedb.org) at setup time; after seeding
the application has no runtime dependency on TMDB. See the in-app `/about` page.

