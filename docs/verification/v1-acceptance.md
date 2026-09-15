# V1 acceptance sign-off

```yaml
status: current
canonical_for: v1-acceptance-evidence
last_verified: 2026-09-15
```

Scope: v1 only, signed off before any v2 work started. Frozen at sign-off time —
item 11's evidence names `LocalArtworkStore`, which v2 replaced with
`MinioArtworkStore` behind the same `ArtworkStore` port (ADR-14); that's expected,
not a discrepancy. See [v2-acceptance.md](v2-acceptance.md) for what's changed
since.

Each acceptance item, with cited evidence in code/tests/config. Test counts at
sign-off: **backend 160**, **frontend 24**, **importer 24** — all green.

| # | Acceptance item | Status | Evidence |
|---|---|---|---|
| 1 | Fresh clone starts from README | ✅ | `README.md` Quick start; `scripts/setup.sh` (validate Docker/Compose, `up -d --build --wait`, health-gated). |
| 2 | Honest empty-app path on missing token | ✅ | `scripts/setup.sh` prints exact seeding instructions and starts an empty app when `TMDB_READ_TOKEN` is absent; `demo/importer/src/main.ts` exits non-zero without faking success. |
| 3 | Idempotent reseed | ✅ | Importer upserts by tmdb ids; `demo/importer/src/importer.test.ts` "second run produces identical record counts"; backend `CatalogueGraphQlIntegrationTest` `createMovie with tmdbId is idempotent`, `addMovieCredit with tmdbCreditId is idempotent`. |
| 4 | Browser uses GraphQL via BFF + HTTP media for artwork bytes | ✅ | GraphQL client lives under `frontend/src/lib/server/` (build fails if imported client-side); `npm run build` succeeds → ADR-11. Artwork bytes via `frontend/src/lib/server/media.ts` relay + `ArtworkServingController`. |
| 5 | Catalogue uses gRPC (not shared DB) for People | ✅ | `PeopleGrpcClient`; ADR-1 evidence (no catalogue imports in people-service; no cross-DB access). Separate datasources per service. |
| 6 | Separate credentials/schema/DB per service | ✅ | `compose.yaml` `catalogue-db` + `people-db` with own volumes/credentials; each service's `application.yml` points only at its own datasource. |
| 7 | Person removal from movie never deletes person; referenced-person delete rejected | ✅ | `PersonUseCases.deletePerson` → `PERSON_IN_USE` when referenced; `PersonUseCasesTest`; `CatalogueGraphQlIntegrationTest` "person in use blocks delete then succeeds after credit removed" (removeMovieCredit leaves person intact). ADR-6. |
| 8 | Credits batch-hydrated (no N+1 gRPC) | ✅ | `PersonHydrator` + `PersonReferenceDataLoader`; `PersonHydratorTest` (single call for many credits); `CatalogueGraphQlIntegrationTest` "movies list ... one batched gRPC call" asserts call count == 1. |
| 9 | Controlled code tables; person roles derived from credits | ✅ | `GenreCode`/`CreditRoleCode` seeded via Flyway; `CreditRules.requireActiveCode` rejects inactive/unknown; roles stored on `movie_credit`. ADR-5. Importer `mappings.ts` never invents codes. |
| 10 | Lists/search bounded and indexed | ✅ | `MovieRules.clampLimit` 1–100; `searchByTitlePattern` uses `ix_movie_title_lower`; People search uses `ix_person_name_lower`; person→movie uses `ix_credit_person`. ADR-8, ADR-9. `SearchUseCasesTest`. |
| 11 | Artwork validated-by-content, size-limited, stored outside Git | ✅ | `media` module `ImageContentValidator` (magic bytes + decode, JPEG/PNG/WebP, 5 MiB) + `LocalArtworkStore` (server keys, traversal-safe, atomic move); `ImageContentValidatorTest`, `LocalArtworkStoreTest`, `ArtworkHttpIntegrationTest`; bytes on named volumes (`compose.yaml`), not Git. ADR-4. |
| 12 | Optimistic locking prevents lost updates | ✅ | `@Version` on movie/person; `updateMovie`/`updatePerson` `expectedVersion` → `CONFLICT`; `CatalogueGraphQlIntegrationTest` "optimistic lock conflict maps to CONFLICT"; People `PersonRepositoryIntegrationTest` stale-update. ADR-13 (credits last-write-wins). |
| 13 | Expected GraphQL/gRPC errors stable and tested | ✅ | `GraphQlExceptionResolver` maps to stable `extensions.code` (§8.2); `CatalogueGraphQlIntegrationTest` asserts BAD_USER_INPUT/NOT_FOUND/CONFLICT/PERSON_IN_USE/DEPENDENCY_UNAVAILABLE; People gRPC status mapping in `PeopleGrpcContractTest` + `PeopleGrpcClientTest`. |
| 14 | Tests run against real PostgreSQL via Testcontainers | ✅ | `*IntegrationTest` classes use `PostgreSQLContainer` + real Flyway migrations. |
| 15 | TMDB attribution present | ✅ | `/about` page (`frontend/src/routes/about/+page.svelte`) + footer with the exact notice; `about/page.test.ts`. §12.5. |
| 16 | README explains architecture, commands, assumptions, trade-offs, AI usage | ✅ | `README.md` sections: Architecture, Commands, Assumptions and trade-offs (ADR links), AI-usage disclosure. |

## Observability (§15)
- Structured JSON logs: `logging.structured.format.console=ecs` in both services;
  correlation id in MDC via `CorrelationIdFilter` / gRPC interceptors.
- Correlation id propagation GraphQL header → MDC → gRPC metadata → People MDC:
  `CorrelationIdFilter`, `CorrelationIdClientInterceptor`,
  `CorrelationIdServerInterceptor`; echoed on responses (verified by
  `CatalogueGraphQlIntegrationTest` "responses carry security headers and echo the
  correlation id").
- Micrometer metrics: `catalogue.person.reference.unavailable`,
  `catalogue.person.hydration.degraded`, `catalogue.artwork.swap.failure`, plus
  auto GraphQL/gRPC/HTTP metrics; `/actuator/prometheus` exposed.
- Liveness + readiness (DB + `peopleGrpc`) health groups.

## Security (§14)
- Secrets only via env; `.env` git-ignored; `.env.example` has no values.
- DBs + People gRPC on the internal Compose network; only Catalogue 8080 published.
- GraphQL depth (12) + complexity (300) limits (`GraphQlSecurityConfig`);
  pagination clamped 1–100.
- Upload byte/type limits; server-generated storage paths; traversal + non-image
  (incl. SVG/GIF) blocked (media module tests).
- Security headers (`SecurityHeadersFilter`); no browser-origin CORS (BFF is
  server-to-server).
- Both service containers run as non-root (`USER appuser`).
- No internal exception details / PII in responses or logs (error boundary +
  filters).

## Time-boxed / not run in this environment
- **Playwright E2E**: authored (`frontend/e2e/journey.spec.ts`), parses via
  `playwright test --list`. Running it needs the composed stack + browsers; steps
  documented in the README.
- **Live TMDB seed**: requires a real `TMDB_READ_TOKEN`. Importer logic, retry,
  idempotency, and failure modes are covered by 24 unit tests.
- **Backend tests on Docker Desktop/Windows** require the `api.version=1.44`
  workaround (docker-java vs. Docker Desktop); Linux CI needs no workaround.
