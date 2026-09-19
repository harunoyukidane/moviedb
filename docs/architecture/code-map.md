# MovieDB code map

```yaml
status: current
canonical_for: file-placement
last_verified: 2026-09-19
```

v1 structure and v2 placement guide. Layer *purpose* and dependency direction are
normative in [overview.md](overview.md); this file only answers "where does this
code belong?" — don't duplicate the former here.

## 1. Design concept

The repository is a small service-oriented monorepo. Top-level folders separate deployable applications, shared backend contracts/adapters, demo tooling, operational scripts, and documentation. Inside each application, code is grouped primarily by business feature and secondarily by architectural responsibility.

The structure follows four principles:

1. **Ownership before reuse.** Movie concepts live in Catalogue and person concepts live in People. Code is shared only when both services need the same infrastructure contract or implementation.
2. **Dependency points inward.** Transport and storage code call application use cases; use cases apply domain rules and depend on ports. Domain code does not import frameworks.
3. **Routes compose; components present.** SvelteKit route files own URL/form boundaries, feature components own substantial UI behavior, and `$lib/server` owns backend integration.
4. **Tests mirror production boundaries.** Unit tests sit beside the module they exercise; integration tests use real PostgreSQL, MinIO, GraphQL, or gRPC at architectural seams.

This is intentionally pragmatic clean architecture. It preserves replaceable boundaries without splitting every layer into its own build artifact.

## 2. Repository map

```text
MovieDB/
├── backend/                    Kotlin/Gradle multi-project build
│   ├── catalogue-service/      Movies, genres, credits, comments, artwork metadata
│   ├── people-service/         People and profile-photo references
│   ├── contracts/              Shared protobuf source and generated gRPC contract
│   ├── media/                  Storage port, validation, local/MinIO adapters
│   ├── gradle/                 Version catalogue and Gradle wrapper
│   └── settings.gradle.kts     Backend module registration
├── frontend/                   SvelteKit BFF and browser UI
│   ├── src/lib/                Shared UI, feature UI, and server-only integration
│   ├── src/routes/             URL-aligned SvelteKit routes
│   ├── src/resources/          Source SVG icons
│   └── e2e/                    Playwright journeys
├── proxy/                      Caddy edge reverse proxy config (ADR-15)
├── demo/importer/              One-shot, idempotent TMDB importer
├── scripts/                    Setup, reset, certificate, and seed scripts
├── docs/                       Architecture, specification, ADRs, and plans
├── compose.yaml                Local deployment topology
└── README.md                   Operator/developer entry point
```

Generated output (`build/`, `.svelte-kit/`, `dist/`) and runtime data (`backend/*/data/`) are not architectural source and must remain ignored.

## 3. Backend modules

### `backend/catalogue-service`

Catalogue is the public application-data service and cross-service orchestrator.

```text
src/main/kotlin/com/moviecatalogue/catalogue/
├── application/       Movie, credit, person, search, reference, and read use cases
├── domain/            Pure catalogue rules and domain exceptions
├── graphql/           GraphQL inbound adapter, SDL mapping, errors, DataLoaders
├── movie/             Movie and movie-genre persistence model/repositories
├── credit/            Movie-credit persistence model/repository
├── reference/         Genre, credit-role, and language controlled-code persistence
├── artwork/           Movie artwork HTTP adapter, metadata, wiring, sweeper
├── people/            Outbound People port and gRPC adapter
├── common/            Small cross-feature primitives such as UUIDv7/pagination
└── observability/     Correlation, health, metrics, and security filters

src/main/resources/
├── graphql/schema.graphqls
├── db/migration/      Catalogue-owned, append-only Flyway migrations
└── application.yml
```

V2 additions should be placed as follows:

- `comment/`: comment entity and repository; application workflow stays in `application/` unless the feature becomes large enough to justify `comment/application`.
- Movie filters: query specifications/repository methods in `movie/`, orchestration in `application/`, contract fields in `graphql/`.
- Expanded genres: an append-only Catalogue Flyway reference-data migration plus the matching version-controlled importer mapping; never edit the applied v1 seed migration.
- MinIO: the reusable adapter belongs in `backend/media`; `artwork/MediaConfig` only selects/configures it.

`graphql/` must remain an inbound adapter. It may map GraphQL inputs/outputs and register DataLoaders, but must not contain business transactions or directly coordinate multiple repositories.

### `backend/people-service`

People is authoritative for person data.

```text
src/main/kotlin/com/moviecatalogue/people/
├── application/       Person lifecycle/search commands and orchestration
├── domain/            Pure person validation and exceptions
├── grpc/              Internal gRPC inbound adapter
├── person/            Person JPA entity and repository
├── photo/             Photo HTTP adapter, use cases, wiring, and orphan sweep
├── common/            Small service-wide primitives
└── observability/     gRPC correlation and operational concerns
```

People must not import Catalogue classes or query Catalogue data. A profile photo is People-owned even though its storage implementation is shared.

### `backend/contracts`

This module owns `.proto` definitions and generated gRPC types. It contains transport contracts, not domain models or shared database entities. Both services may depend on the contract; neither should depend on the other service module.

Contract changes should be backward-compatible where possible: add fields with new field numbers, never reuse removed numbers, and keep status semantics covered by contract tests.

### `backend/media`

This module is a narrow shared infrastructure library:

```text
media/
├── ArtworkStore.kt          Storage-neutral application port
├── ImageContentValidator.kt Shared content validation
├── LocalArtworkStore.kt     Local/test adapter
├── MinioArtworkStore.kt     V2 deployed adapter
├── WebpEncoder.kt           Best-effort `cwebp` transcoder for serving variants
└── Exceptions.kt            Storage/validation failures
```

It must not know about movies, people, JPA entities, GraphQL, or HTTP controllers. Keeping it domain-neutral lets both owning services reuse storage mechanics without creating shared ownership of metadata.

## 4. Backend package rules

| Area | May contain | Must not contain |
|---|---|---|
| `domain` | Pure validation, value rules, domain exceptions | Spring controllers, JPA queries, MinIO/gRPC clients |
| `application` | Use cases, commands, transactions, orchestration | GraphQL/HTTP request types, rendered error messages |
| Feature persistence packages | Entities, repositories, explicit database queries | Cross-service database access, UI decisions |
| Inbound adapters | Protocol parsing, mapping, error/status translation | Multi-step business workflow, direct storage logic |
| Outbound adapters | SDK/framework calls, timeouts, infrastructure mapping | UI behavior or unrelated domain ownership |
| `common` | Stable, genuinely cross-feature primitives | A miscellaneous dumping ground |

A new abstraction is warranted when it protects a real boundary or has multiple implementations. Do not introduce an interface for every class solely to match a diagram.

## 5. Frontend structure

```text
frontend/src/
├── routes/
│   ├── +layout.svelte
│   ├── +page.ts                  Root redirect; universal because it needs no secret
│   ├── movies/
│   │   ├── +page.server.ts       Listing load/filter boundary
│   │   ├── +page.svelte          Listing page composition
│   │   ├── new/                  Create route
│   │   └── [id]/                 Dynamic movie route
│   │       ├── +page.*            Detail route
│   │       └── edit/+page.*       Edit route
│   ├── people/                   Equivalent person routes
│   └── api/                      Same-origin search/media endpoints
├── lib/
│   ├── components/              Reusable, domain-light controls
│   ├── features/                V2 feature-oriented UI components
│   │   ├── movies/
│   │   ├── credits/
│   │   ├── comments/
│   │   └── people/
│   ├── server/                  Server-only GraphQL/media integration
│   └── errors.ts                Browser-safe error presentation types/helpers
├── resources/                  SVG source assets
├── app.css
└── app.html
```

`lib/features` is the v2 target for substantial domain-aware UI extracted from route pages. It does not need to be created in advance; add each folder with the first real feature component.

### SvelteKit naming

- `[id]` is a dynamic route parameter and is valid SvelteKit naming. Use `[movieId]` only if the extra semantic name measurably improves a complex route; consistency is more valuable than a cosmetic rename.
- `+page.svelte` defines a page component.
- `+page.ts` defines a universal load function and is appropriate when no server-only dependency is needed.
- `+page.server.ts` defines server-only loading and form actions. Use it whenever internal URLs, backend calls, secrets, cookies, or privileged validation are involved.
- `+server.ts` defines an HTTP endpoint rather than a page.
- Files without a `+` prefix are ordinary modules/components and are not SvelteKit route entry points.

These names are framework contracts, not project-specific naming mistakes.

### Frontend responsibility rules

- Route server files parse request data, call `$lib/server` operations, and translate failures once.
- `operations.ts` owns typed GraphQL documents/functions; it does not own form parsing.
- `request.ts` owns correlation context and common load-error conversion.
- `media-proxy.ts` owns cacheable streaming response behavior.
- Feature components own presentation and interaction state, not backend addresses.
- Query-string state is preferred for shareable filters and pagination. A view preference may use a query parameter or local storage, but its default is `cluster` and changing it must preserve the current offset.
- Source SVGs should be consumed through a consistent icon component or build-supported import, with an accessible name on every icon-only control.

## 6. Tests

Production and test trees mirror one another. Naming communicates scope:

- `*Test.kt`: domain/application unit test unless it explicitly boots infrastructure.
- `*IntegrationTest.kt`: Spring, PostgreSQL, GraphQL, gRPC, HTTP, or MinIO boundary test.
- `*.test.ts`: TypeScript or Svelte component/unit test.
- `frontend/e2e/*.spec.ts`: Playwright user journey.

V2 MinIO tests should use a real MinIO Testcontainer for `put`, `open`, `exists`, `delete`, `listKeys`, missing keys, and service configuration. Use a mocked `ArtworkStore` only for testing application-side compensation ordering.

## 7. Adding a feature

Use this sequence to keep dependencies clean:

1. Define observable behavior and domain rules.
2. Add an append-only database migration or contract change when required.
3. Implement/test domain and application behavior.
4. Implement the persistence or external adapter.
5. Expose it through GraphQL, gRPC, or HTTP.
6. Add/update `$lib/server` types and operations.
7. Compose the route from feature/shared components.
8. Add boundary integration tests and the smallest valuable E2E coverage.
9. Update [requirements.md](../product/requirements.md)/this doc as needed and add an ADR for a hard-to-reverse decision.

This order keeps UI and transport code from becoming the accidental source of business truth.

## 8. Known cleanup direction

- Keep GraphQL controllers dependent on application read/write services, not repositories.
- Add batched database projections/DataLoaders if measurements show nested GraphQL database N+1 behavior; gRPC person hydration is already batched.
- Extract v2 movie listing, credit display, comment, and people-row UI into feature components as those screens grow.
- Keep storage selection configuration-driven and remove deployed local-volume mounts only after MinIO integration and rollback tests pass.
- Do not create a generic `utils` package; name helpers after their responsibility and place them at the narrowest shared scope.
