# Implementation Phase 1 — Scaffolding & Spec Edits

Status: ready to execute
Depends on: none (first phase)
Reference: TECHNICAL_SPECIFICATION v0.1.md §7 (data design), §17 (repo structure), §18 (sequence), §19 (acceptance)

## Objective

Stand up the project skeleton so every later phase has a runnable, testable foundation:

- Gradle Kotlin DSL multi-project backend with a shared protobuf `contracts` module.
- Two Spring Boot service skeletons: `catalogue-service` and `people-service`.
- Two independent PostgreSQL databases with Flyway migrations implementing the full §7 schema plus seed reference data.
- Docker Compose topology wiring services + databases with health-gated startup.
- Health/liveness/readiness endpoints.
- CI skeleton.
- Apply the confirmed decision edits to `TECHNICAL_SPECIFICATION v0.1.md` in place (see "Spec edits" below).

At the end of this phase, `docker compose up --wait` must bring both services up healthy against their own databases with all migrations applied, and the repository/integration tests must pass.

## Confirmed decisions in force this phase

1. **UUIDv7** for all primary keys (time-ordered). Generate in application code (Kotlin) at entity creation; the DB column type stays `UUID`. Do not rely on `gen_random_uuid()` (that is UUIDv4).
2. **BFF pattern** — recorded in spec; no frontend work this phase.
3. **death_date** present in the proto `CreatePersonRequest` — reflected in the contracts module authored here (proto is finalized in phase 2, but the corrected shape is recorded in the spec now).
4. **Credit-lock note** — spec only this phase.
5. **Person-image hybrid** — spec only this phase; `person.profile_path` column already exists in §7.3 DDL and is retained.

## Repository structure to create (§17)

```text
movie-catalogue/
├── README.md                      (stub; completed in phase 8)
├── compose.yaml
├── .env.example                   (no secret values)
├── .gitignore                     (ignore .env, build outputs, artwork volume, node_modules)
├── docs/
│   ├── architecture.md            (stub)
│   └── decisions/                 (ADR folder; ADRs added as phases land)
├── backend/
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   ├── gradle/                    (wrapper + version catalog libs.versions.toml)
│   ├── contracts/
│   │   └── src/main/proto/catalogue/people/v1/people.proto
│   ├── catalogue-service/
│   │   ├── build.gradle.kts
│   │   └── src/main/{kotlin,resources}
│   │       └── resources/db/migration/   (Flyway V1__*.sql for catalogue DB)
│   └── people-service/
│       ├── build.gradle.kts
│       └── src/main/{kotlin,resources}
│           └── resources/db/migration/   (Flyway V1__*.sql for people DB)
├── scripts/                       (stubs; completed in phase 7)
└── .github/workflows/ci.yml
```

Frontend and demo importer directories are created in their own phases (5 and 7) to keep this phase focused.

## Tasks (TDD, in order)

### 1. Backend Gradle multi-project

- Create `backend/settings.gradle.kts` including `contracts`, `catalogue-service`, `people-service`.
- Root `build.gradle.kts`: apply Kotlin JVM 21 toolchain, Spring Boot 3.5.x + Spring Dependency Management, common test config (JUnit 5 platform).
- Add a Gradle version catalog `gradle/libs.versions.toml` pinning: Kotlin, Spring Boot, gRPC, protobuf, Flyway, PostgreSQL driver, Testcontainers, JUnit 5, MockK, AssertJ. Pin exact versions.
- Commit the Gradle wrapper.

### 2. Contracts module

- `backend/contracts` applies the protobuf + gRPC codegen plugins.
- Author `people.proto` per spec §8.3 **with the confirmed fix**: `CreatePersonRequest` includes `optional string death_date`.
- Package `catalogue.people.v1`; verify it compiles/generates code. Full service implementation is phase 2; here we only need generation to succeed so both services can depend on the module.

### 3. People Service skeleton

- Spring Boot app (`people-service`) with dependencies: Spring Boot Web/Actuator, Spring Data JPA, PostgreSQL driver, Flyway, gRPC server starter, the `contracts` module.
- `application.yml`: datasource from env vars (`PEOPLE_DB_URL`, `PEOPLE_DB_USER`, `PEOPLE_DB_PASSWORD`), Flyway enabled, Actuator health groups `liveness`/`readiness`, gRPC server port.
- Flyway `V1__init_people.sql` implementing §7.3 `person` table + `ix_person_name_lower` + the `death_date >= birth_date` check. UUID PKs are supplied by the app (no DB default).
- Minimal `@SpringBootApplication`; readiness includes DB check.

### 4. Catalogue Service skeleton

- Spring Boot app (`catalogue-service`) with dependencies: Spring Boot Web/Actuator, Spring for GraphQL, Spring Data JPA, PostgreSQL driver, Flyway, gRPC client starter, the `contracts` module.
- `application.yml`: datasource from env vars (`CATALOGUE_DB_URL`, etc.), Flyway enabled, Actuator health groups, People gRPC client target from env (`PEOPLE_GRPC_TARGET`), placeholder GraphQL config.
- Flyway `V1__init_catalogue.sql` implementing the full §7.2 DDL: `movie`, `credit_category` enum, `credit_role_code`, `genre_code`, `movie_genre`, `movie_credit` (with composite FK + cast/crew check + both unique indexes), `artwork_asset` (+ `uq_movie_primary_artwork`), all listed B-tree indexes.
- Flyway `V2__seed_reference_data.sql` inserting the seed `credit_role_code` and `genre_code` rows from §7.2.
- Minimal `@SpringBootApplication`; readiness includes DB check (gRPC-connectivity readiness is added in phase 3 when the client is used).

### 5. Docker Compose (`compose.yaml`)

- Services: `catalogue-db` (postgres), `people-db` (postgres), `catalogue-service`, `people-service`.
- Each DB gets its own named volume and its own credentials (separate DB per service — acceptance §19).
- Databases and the People gRPC port bind to the internal Compose network only; publish only the Catalogue HTTP port and the People/Catalogue Actuator as needed for the reviewer (§14).
- `healthcheck` for each DB (`pg_isready`) and each service (Actuator readiness). Services `depends_on` DBs with `condition: service_healthy`.
- Env wired from `.env` (`.env.example` documents keys; no secret values committed).

### 6. CI skeleton (`.github/workflows/ci.yml`)

- Jobs: backend build + unit/integration tests (`./gradlew build`), format/lint check. Frontend/E2E jobs are stubbed and filled in later phases.

## Test requirements (this phase)

Repository/integration tests via **Testcontainers PostgreSQL** (real Postgres, real migrations):

- People DB: migration applies cleanly; `person` inserts with app-supplied UUIDv7; `death_date < birth_date` rejected; `death_date >= birth_date` and null cases accepted; `@Version` optimistic-lock column increments on update.
- Catalogue DB: migration applies cleanly; seed reference rows present; `movie_credit` cast-requires-character / crew-forbids-character check enforced; composite FK `(role_code, category)` rejects mismatch; `uq_movie_primary_artwork` enforces one primary artwork per movie; `movie_genre` PK prevents duplicate assignment; `ON DELETE CASCADE` removes credits/genres/artwork when a movie is deleted; `byte_size` bounds (0 < size ≤ 5 MiB) enforced.
- Verify UUIDv7 generation utility produces time-ordered, monotonic-ish values and valid UUID format (unit test).

All tests must run in CI against Testcontainers, not an embedded DB.

## Spec edits to apply this phase (edit TECHNICAL_SPECIFICATION v0.1.md **in place**)

1. **§7 (or §7.1) — UUIDv7 reasoning.** Add a note: primary keys use application-generated UUIDv7 (time-ordered). Rationale: time-ordered keys reduce B-tree index fragmentation and improve insert locality and range-scan performance versus random UUIDv4, regardless of scale; external `tmdb_id` remains nullable provenance, not the PK.
2. **§8.3 proto — death_date.** Add `optional string death_date = 7;` (renumber subsequent fields as needed, never reusing numbers) to `CreatePersonRequest` so a person can be created deceased, matching `PersonPatch` and the DB.
3. **§8.1 / §7.4 — credit-lock note.** Record explicit decision: `updateMovieCredit` intentionally has no `expectedVersion`; credit rows are small and rarely concurrently edited, so last-write-wins is accepted for credits while movies/people keep optimistic locking. Note it as an accepted trade-off.
4. **§10 / §11 — person-image hybrid decision (1=b).** Record: person profile images are a hybrid — the TMDB `profile_path` URL is captured at import as provenance and displayed until replaced; the only in-app control is uploading a photo through the same validated `ArtworkStore` path as movie artwork. Rendering the stored URL is not a live runtime TMDB dependency.
5. **§1 / §5 — BFF pattern.** Record: the browser talks to SvelteKit server routes; SvelteKit calls the Catalogue GraphQL API server-side (BFF). GraphQL is not exposed to the browser directly; this removes browser-to-backend CORS and lets correlation IDs propagate server-side.

Add corresponding ADR stubs under `docs/decisions/` for decisions that map to §20 (UUIDv7 choice can extend ADR-8's pagination/keys discussion or be its own ADR; BFF and person-image are new ADRs).

## Demo statement (phase acceptance)

- `docker compose up --wait` starts `catalogue-db`, `people-db`, `people-service`, `catalogue-service`, all reporting healthy.
- Both databases have their migrations applied and reference data seeded (Catalogue).
- `./gradlew build` runs green including Testcontainers integration tests listed above.
- `TECHNICAL_SPECIFICATION v0.1.md` reflects the five confirmed edits.
