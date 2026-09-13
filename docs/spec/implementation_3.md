# Implementation Phase 3 — Catalogue GraphQL Layer

Status: ready to execute
Depends on: phase 1 (scaffolding, catalogue DB), phase 2 (People gRPC live)
Reference: TECHNICAL_SPECIFICATION v0.1.md §6.1/§6.5/§6.6 (lifecycles), §7.2 (DDL), §8.1 (SDL), §8.2 (error contract), §9 (search stub), §13 (resilience)

## Objective

Implement the Catalogue Service public GraphQL API and resolvers: movie CRUD, controlled genre + credit-role reference reads, movie-credit mutations, person GraphQL orchestration (create/update/delete via People gRPC) with safe delete, and batched person hydration with no N+1 gRPC calls. Enforce the stable GraphQL error contract and optimistic locking.

Search itself is a later phase (6); this phase wires the schema and the non-search resolvers. Artwork bytes are phase 4; here the `Movie.artwork` field returns metadata/URL only (may be null until phase 4).

## GraphQL contract (§8.1)

Commit the SDL as a reviewed contract. Implement the `Query`/`Mutation`/type shapes exactly as in §8.1: `movie`, `movies`, `person`, `people`, `genres`, `creditRoles` queries; movie/person/credit mutations; `expectedVersion: Long!` on `updateMovie`/`updatePerson`. `updateMovieCredit` has **no** expectedVersion (recorded accepted decision — last-write-wins for credits).

## Architecture

- **Domain:** `Movie`, `MovieCredit` invariants (cast requires character; crew forbids character; role/category agreement; billing order ≥ 0), `GenreCode`, `CreditRoleCode`. Pure Kotlin.
- **Application use cases:** `GetMovieDetails`, `ListMovies`, `CreateMovie`, `UpdateMovie` (optimistic lock), `DeleteMovie` (cascade credits/genres/artwork), `AddCredit`, `UpdateCredit`, `RemoveCredit`, person orchestration (`CreatePerson`/`UpdatePerson`/`DeletePerson` via gRPC), `ListGenres`, `ListCreditRoles`.
- **Outbound ports:** `MovieRepository`, `CreditRepository`, code-table repositories, and `PeopleClient` (gRPC adapter with deadlines).
- **Inbound adapter:** Spring for GraphQL resolvers + a single error-mapping boundary producing stable `extensions.code`.

## Key behaviours

### Batched credit hydration (§6.5 — no N+1)
1. Load movie + credits + artwork metadata in bounded queries.
2. Extract distinct person IDs.
3. One batched `GetPeople` gRPC call with a deadline (~1s read).
4. Map people onto credits; missing/unavailable person -> `PersonReference.available = false`, log with correlation ID, do not fail the whole movie page (§13).
- Use a GraphQL `BatchLoader`/DataLoader so `movies` list + nested credits also batch person lookups across the page — never one gRPC call per credit.

### Add credit (§6.6)
1. Validate shape/required fields.
2. `GetPerson` gRPC to validate the logical reference (deadline; `UNAVAILABLE` -> `DEPENDENCY_UNAVAILABLE`, do not insert).
3. Insert credit in a Catalogue DB transaction subject to uniqueness constraints (`uq_movie_credit_manual`).
4. Return credit with hydrated person.

### Person safe delete
- `deletePerson` first checks local `movie_credit` for references; if any -> reject `PERSON_IN_USE`; else call People `DeletePerson` gRPC. Person removal from a movie is a credit removal, never a person delete.

### Error contract (§8.2) — stable extensions.code
`BAD_USER_INPUT`, `NOT_FOUND`, `CONFLICT` (duplicate or stale version), `PERSON_IN_USE`, `DEPENDENCY_UNAVAILABLE`, `INTERNAL_ERROR` (no stack traces/PII leaked). `PAYLOAD_TOO_LARGE`/`UNSUPPORTED_MEDIA_TYPE` arrive with artwork in phase 4.

## Tasks (TDD, in order)
1. Domain models + rules with unit tests (cast/crew character rules, role/category agreement, billing order, inactive/unknown code rejection, duplicate-credit detection).
2. Repositories + Testcontainers integration (movie CRUD, credit constraints, cascade delete, optimistic lock, genre association, code-table reads).
3. `PeopleClient` gRPC adapter with deadlines + status->exception mapping; timeout/unavailable tests (can use in-process People or a stub).
4. Application use cases + unit tests (batched IDs, gRPC timeout mapping to `DEPENDENCY_UNAVAILABLE`, no repository write after validation failure, person-in-use).
5. GraphQL SDL + resolvers + DataLoader batching; single error-mapping boundary.
6. GraphQL contract/integration tests.

## Test requirements
Unit: cast requires character; role/category agreement; inactive/unknown role & genre codes rejected; negative runtime/billing order rejected; duplicate credit; person-in-use deletion rejected.
Application unit: batched person IDs (one gRPC call for many credits); gRPC timeout -> `DEPENDENCY_UNAVAILABLE`; no write after validation failure; compensation ordering.
Integration/contract: GraphQL schema shape; null/error mapping to stable codes; optimistic-lock `CONFLICT`; movie+credit+genre lifecycle end-to-end against real People gRPC; degraded hydration returns `available:false` without failing the page; pagination bounds and limit clamp (1–100).
Edge: movie/person/credit not found; max page size; invalid offsets; duplicate movie-genre; role/category mismatch.

## Demo statement (phase acceptance)
- Full movie + credit + genre lifecycle works over GraphQL against the real People gRPC service.
- Listing movies with many credits performs a bounded number of gRPC calls (batched), verified by test.
- Person delete is rejected while referenced (`PERSON_IN_USE`) and succeeds once unreferenced; removing a credit never deletes the person.
- Degraded People Service yields `available:false` references, not a failed movie page.
- All expected error codes are stable and tested.

## ADR
- ADR-2 (PostgreSQL + normalized MovieCredit), ADR-3 (GraphQL external / gRPC internal), ADR-5 (controlled code tables; roles on credit), ADR-6 (physical deletion with reference protection) — record with evidence links to code/tests.
