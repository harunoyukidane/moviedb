# Implementation Phase 2 — People Service (gRPC)

Status: ready to execute
Depends on: phase 1 (scaffolding, contracts module, people DB migration)
Reference: TECHNICAL_SPECIFICATION v0.1.md §6.2 (People responsibilities), §7.3 (person DDL), §8.3 (proto), §13 (resilience), §16 (tests)

## Objective

Implement the People Service as the authoritative owner of people, exposing only an internal gRPC API. It must not know how movie credits are stored and must never query the Catalogue database.

Deliver gRPC endpoints for single lookup, batch lookup, search, create, update, and delete, with correct deadlines, batch-size caps, ID de-duplication, and deliberate gRPC status mapping.

## gRPC surface (from §8.3, with confirmed death_date on create)

- `GetPerson(GetPersonRequest) -> PersonResponse`
- `GetPeople(GetPeopleRequest) -> GetPeopleResponse` — batch, single `WHERE id IN (...)` query
- `SearchPeople(SearchPeopleRequest) -> SearchPeopleResponse`
- `CreatePerson(CreatePersonRequest) -> PersonResponse` — includes `death_date`
- `UpdatePerson(UpdatePersonRequest) -> PersonResponse` — `expected_version` + field mask
- `DeletePerson(DeletePersonRequest) -> Empty`

## Architecture (layer-by-feature, §6.4)

- **Domain:** `Person` model + invariants (name required/trimmed/length bounds; `death_date >= birth_date`; version). Pure Kotlin, unit-testable without Spring.
- **Application use cases:** `GetPerson`, `GetPeople`, `SearchPeople`, `CreatePerson`, `UpdatePerson`, `DeletePerson`. Enforce batch cap, dedup, offset/limit clamping.
- **Outbound port:** `PersonRepository` (Spring Data JPA adapter). Search uses explicit `lower(name)` matching with escaped wildcards.
- **Inbound adapter:** gRPC service implementation mapping proto <-> domain and exceptions -> gRPC `Status`.

## Tasks (TDD, in order)

### 1. Domain model + rules (write tests first)
- `Person` entity/value with validation. Map `@Version` for optimistic locking.
- Date-string parsing/formatting helpers (proto uses `optional string` ISO dates).

### 2. Repository adapter + integration
- JPA entity mapping to `person`; UUIDv7 assigned by app on create.
- Query methods: `findById`, `findAllByIdIn`, paginated case-insensitive name search (`lower(name) LIKE` with `%`/`_` escaped), `existsByTmdbId` for idempotent import upserts.

### 3. Application use cases
- `GetPeople`: dedupe requested IDs at the caller boundary, cap at 200 (§8.3), single `IN` query, return results keyed by ID; missing IDs simply absent (caller decides how to represent).
- `SearchPeople`: clamp limit to 1–100, validate min/max query length, return `total`.
- `CreatePerson`: honor `tmdb_id` idempotency where provided; validate; assign UUIDv7. Accepts `death_date`.
- `UpdatePerson`: apply field mask to `PersonPatch`; enforce `expected_version` -> map stale to `ABORTED`.
- `DeletePerson`: physical delete (People holds no credit knowledge; referential protection is orchestrated by Catalogue in phase 3).

### 4. gRPC inbound adapter + status mapping
- Map exceptions deliberately: validation -> `INVALID_ARGUMENT`; not found -> `NOT_FOUND`; duplicate `tmdb_id` -> `ALREADY_EXISTS`; stale version -> `ABORTED`; precondition (e.g. bad field mask) -> `FAILED_PRECONDITION`; downstream/DB down -> `UNAVAILABLE`.
- Server-side deadlines honored; reject oversized batches with `INVALID_ARGUMENT`.
- Bind gRPC to internal Compose network only (§14).

### 5. Readiness
- People readiness reports DB connectivity (already stubbed in phase 1; ensure it reflects real DB state).

## Test requirements

Unit (MockK + AssertJ, no Spring):
- Name blank/whitespace/overlong rejected; valid accepted.
- `death_date` before `birth_date` rejected; equal/after/null accepted.
- Optimistic-lock: stale `expected_version` -> `ABORTED` mapping.
- Batch dedup + cap enforcement; limit/offset clamping; query-length validation.

Edge:
- Blank/overlong names and search queries.
- Duplicate `tmdb_id` on create -> `ALREADY_EXISTS`.
- `GetPeople` with duplicate IDs, missing IDs, and IDs returned out of order (caller must key by ID, not position).
- Search wildcard literals `%`, `_`, apostrophes, Unicode, mixed case.

gRPC in-process contract tests:
- Each RPC returns the correct proto shape and status codes.

Integration (Testcontainers PostgreSQL, real Flyway migration):
- CRUD round-trips; case-insensitive search uses `ix_person_name_lower`; optimistic-lock increments; pagination correctness.

## Demo statement (phase acceptance)

- A gRPC client (test harness) can create (incl. deceased), get, batch-get (deduped, keyed by ID), search, update (with version check), and delete people, each returning the correct status codes.
- Unit, edge, in-process contract, and Testcontainers integration tests pass in CI.
- People Service starts healthy in Compose with gRPC bound internally only.

## ADR
- Add/confirm ADR-1 (service split by data ownership) evidence: People has zero Catalogue coupling.
