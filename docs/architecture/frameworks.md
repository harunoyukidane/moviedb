# Frameworks and runtime stack

```yaml
status: current
canonical_for: technology-versions
last_verified: 2026-09-19
```

What the code is built out of, at which version, and the convention each choice
imposes on code written against it. [overview.md](overview.md) explains *why* a
technology was chosen; this file is the inventory and the house rules that follow
from it.

Versions below are read from
[`backend/gradle/libs.versions.toml`](../../backend/gradle/libs.versions.toml),
[`frontend/package.json`](../../frontend/package.json),
[`demo/importer/package.json`](../../demo/importer/package.json) and
[`compose.yaml`](../../compose.yaml). Those files are authoritative — if
they disagree with this table, they win and this file is stale.

## 1. Backend

| Concern | Technology | Version |
|---|---|---|
| Language | Kotlin (JVM target 21, `-Xjsr305=strict`) | 1.9.25 |
| Application framework | Spring Boot | 3.5.4 |
| Public API | Spring for GraphQL (`spring-boot-starter-graphql`) | via Boot BOM |
| Persistence | Spring Data JPA / Hibernate | via Boot BOM |
| Migrations | Flyway (`flyway-core` + `flyway-database-postgresql`) | 10.20.1 |
| Database driver | PostgreSQL JDBC | 42.7.4 |
| Internal RPC | gRPC Java / gRPC Kotlin | 1.68.1 / 1.4.1 |
| gRPC ↔ Spring wiring | `net.devh:grpc-{server,client}-spring-boot-starter` | 3.1.0.RELEASE |
| Contracts | protobuf (java + kotlin), protoc plugin | 3.25.5 / 0.9.4 |
| Object storage | MinIO Java SDK (+ OkHttp) | 9.0.3 / 4.12.0 |
| Primary keys | `com.github.f4b6a3:uuid-creator` (UUIDv7, ADR-10) | 6.0.0 |
| Image decoding | TwelveMonkeys `imageio-webp` | 3.11.0 |
| Logging API | SLF4J | 2.0.16 |
| Build | Gradle multi-project, version catalogue | see `gradle/wrapper` |

### Conventions these impose

- **Kotlin + JPA need the `kotlin-jpa` and `kotlin-spring` compiler plugins**
  (applied in each of the two service modules) to open classes and synthesize
  no-arg constructors. Do not work around a missing plugin by making an entity
  `open` by hand.
- **`@Transactional` is proxy-based.** A call from one method of a bean to
  another method of the *same* bean bypasses the proxy and runs outside the
  transaction. Transaction boundaries belong on the application-service method
  the adapter actually calls.
- **Flyway migrations are append-only and immutable once applied.** Add `V11__…`;
  never edit an applied file. Reference-data changes are migrations too.
- **Spring for GraphQL resolves nested fields lazily**, which is where N+1
  behaviour appears. Batch with a `DataLoader` (see
  `graphql/PersonReferenceDataLoader`), not with a loop in a `@SchemaMapping`.
- **Generated protobuf/gRPC code lives in `contracts`** and is not edited.
  Contract changes add field numbers and never reuse a removed one.

## 2. Frontend

| Concern | Technology | Version |
|---|---|---|
| Framework | SvelteKit | 2.8.1 |
| Component model | Svelte (v4 syntax — stores and `export let`, not runes) | 4.2.19 |
| Deployment target | `@sveltejs/adapter-node` | 5.2.9 |
| Build / dev server | Vite (`@sveltejs/vite-plugin-svelte` 3.1.2) | 5.4.11 |
| Language | TypeScript | 5.5.4 |
| Type checking | `svelte-check` (`npm run check`) | 3.8.6 |
| GraphQL client | `graphql-request` over `graphql` | 6.1.0 / 16.9.0 |

### Conventions these impose

- **Svelte 4, not 5.** No runes (`$state`, `$derived`, `$props`). Reactivity is
  `$:` and stores; props are `export let`.
- **`$lib/server/**` never reaches the browser.** SvelteKit fails the build if a
  client module imports one. Type-only imports of `$lib/server/types` are fine
  and are erased — see [code-graph.md](code-graph.md#4-frontend-module-graph).
- **Form actions return `ActionResult`;** progressive enhancement goes through
  `use:enhance` with `({ result, update })`, so every form still works with
  JavaScript disabled.
- **The BFF owns no business rules.** Validation in `$lib/server/validation.ts`
  exists for immediate feedback; the services revalidate authoritatively.
- **CSP is emitted by SvelteKit itself** (`kit.csp` in `svelte.config.js`), only
  on a real rendered response — which is why it is asserted by a Playwright spec
  rather than a unit test.

## 3. Demo tooling (`demo/importer`, `demo/loadtest`)

Two standalone npm projects, not part of the frontend build and not shipped with
the application. Both are plain TypeScript on Node, run as one-shot Compose
services under a profile.

| Concern | Technology | Version |
|---|---|---|
| Language / runtime | TypeScript on Node 20 | 5.5.4 |
| Catalogue calls | `graphql-request` over `graphql` | 6.1.0 / 16.9.0 |
| People calls (importer only) | `@grpc/grpc-js` + `@grpc/proto-loader` | 1.11.3 / 0.7.13 |
| Tests (importer only) | Vitest | 1.6.0 |

### Conventions these impose

- **The importer seeds through the public APIs**, never the database — People
  gRPC, Catalogue GraphQL, artwork HTTP. It therefore exercises the same
  validation a user hits, and a rerun must stay idempotent (`tmdbId` and
  `tmdbCreditId` are the upsert keys, ADR-7).
- **`@grpc/proto-loader` reads `people.proto` at runtime**, so the importer needs
  no generated stubs — but it does need the proto file present in its image.
- **The load test is deliberately untested.** It is a diagnostic harness; its
  findings are recorded in
  [verification/loadtest-findings.md](../verification/loadtest-findings.md).

## 4. Testing

| Layer | Technology | Version |
|---|---|---|
| Backend test runner | JUnit 5 (Jupiter) | 5.11.3 |
| Backend assertions | AssertJ | 3.26.3 |
| Backend mocking | MockK | 1.13.13 |
| Real infrastructure | Testcontainers (`junit-jupiter`, `postgresql`, `minio`) | 1.21.4 |
| GraphQL testing | `spring-graphql-test` | via Boot BOM |
| gRPC testing | `grpc-inprocess`, `grpc-testing` | 1.68.1 |
| Frontend unit/component | Vitest + jsdom | 1.6.0 / 24.1.3 |
| Importer unit | Vitest (its own project, `demo/importer`) | 1.6.0 |
| Component driving | `@testing-library/svelte` + `user-event` + `jest-dom` | 4.2.3 / 14.5.2 / 6.6.3 |
| End-to-end | Playwright | 1.48.2 |

Naming is the scope signal, and it is load-bearing:
`*Test.kt` = unit unless it boots infrastructure · `*IntegrationTest.kt` = real
PostgreSQL/MinIO/GraphQL/gRPC · `*ContractTest.kt` = a cross-boundary contract
pinned in place · `*.test.ts` = Vitest · `e2e/*.spec.ts` = Playwright. The importer's
`src/*.test.ts` run under their own Vitest project, so they need a separate
command — `npm test` in `frontend/` does not reach them.

The full per-test enumeration is [test-specs.xlsx](../verification/test-specs.xlsx);
the strategy behind it is [verification/README.md](../verification/README.md).

### One build quirk worth knowing

`backend/build.gradle.kts` pins `systemProperty("api.version", "1.44")` for every
`Test` task. Docker Desktop on Windows rejects the Docker API version
Testcontainers negotiates by default and returns an HTTP 400, which surfaces as
the misleading "Could not find a valid Docker environment". Override or opt out
with `-PdockerApiVersion=<ver>` (empty string disables it, e.g. on Linux CI).

## 5. Runtime images

From [`compose.yaml`](../../compose.yaml):

| Service | Image | Published port |
|---|---|---|
| `proxy` (Caddy, ADR-15) | `caddy:2.8-alpine` | `4173` — the only published entry point |
| `frontend` (BFF) | built on `node:20-alpine` | internal `3000` |
| `catalogue-service` | built on `eclipse-temurin:21-jre` | `8080` |
| `people-service` | built on `eclipse-temurin:21-jre` | internal (HTTP + gRPC) |
| `catalogue-db`, `people-db` | `postgres:16-alpine` | internal |
| `minio` | `quay.io/minio/minio:RELEASE.2023-09-04T19-57-37Z` | `9000` API, `9001` console |
| `minio-init` | `quay.io/minio/mc:latest` | one-shot bucket/identity/policy bootstrap |

`minio-init` is the only component that holds MinIO root credentials, and it
exits once the per-service least-privilege identities exist. No application ever
receives root.

## 6. Upgrading

Versions live in exactly two places — the Gradle version catalogue and
`package.json` — so an upgrade is a one-line change plus a test run. Order of
risk, highest first:

1. **Spring Boot major/minor** — moves Hibernate, Jackson and the servlet stack
   with it. Run the full integration suite, not just unit tests.
2. **Svelte 4 → 5** — a syntax migration, not a version bump. It would touch
   every component; treat it as a project, and write an ADR.
3. **gRPC + protobuf** — must move together, and `net.devh` must support the
   Boot version. Contract tests are the gate.
4. **Testcontainers / Playwright / Vitest** — test-only blast radius.
5. **Base images** — `postgres:16-alpine` is pinned to a major; MinIO is pinned
   to an exact release because its admin API surface moves.

## Related

- [Architecture overview](overview.md) — why these were chosen
- [Code graph](code-graph.md) — what actually imports what
- [Code map](code-map.md) — where a new file belongs
- [ADRs](../decisions/README.md) — the hard-to-reverse calls
