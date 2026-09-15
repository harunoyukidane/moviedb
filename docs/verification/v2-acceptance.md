# V2 acceptance

```yaml
status: current
canonical_for: v2-acceptance-evidence
last_verified: 2026-09-15
```

Scope: v2 only. Updated as tasks in [plans/v2/](../plans/v2/README.md) land — do
not treat [v1-acceptance.md](v1-acceptance.md)'s test counts as covering any item
below.

| Criterion | Status | Evidence |
|---|---|---|
| Catalogue and People use MinIO in Compose through `ArtworkStore`, separate buckets, no service-local artwork mounts | ✅ done | `backend/media/.../MinioArtworkStore.kt`; `ObjectStorageHealthIndicator.kt`; `ArtworkMinioHttpIntegrationTest`, `ArtworkOrphanSweeperTest`, `MinioArtworkStoreTest` |
| Existing media HTTP behavior, validation, ETag/cache headers, compensation, orphan cleanup pass against MinIO | ✅ done | `ArtworkExceptionAdviceTest`, `ArtworkOrphanSweeperTest` |
| Genre/year filters compose correctly, reset offset, preserve accurate totals | ✅ done | `MovieFilterInput` in `schema.graphqls`; `MovieRepository.findAllByFilter`, `MovieUseCasesTest`, `CatalogueRepositoryIntegrationTest`, `CatalogueGraphQlIntegrationTest` (backend); `movies/+page.server.ts`, `MovieFilters.svelte`, `page.server.test.ts`, `MovieFilters.test.ts` (frontend BFF/UI) |
| Seed dataset spans enough genres/years to demonstrate both filters | ✅ done | `V3__expand_genre_reference_data.sql` (9 controlled genres); `GENRE_MAP` in `mappings.ts`; `demo/tmdb-movie-ids.txt` (26 ids, 1949–2019); `CatalogueSchemaIntegrationTest`, `importer.test.ts`, `tmdb.test.ts` (manifest/coverage assertions). Live-verified: `scripts/seed-host.ps1` imported all 26/26 with posters against real TMDB; resulting catalogue covers all 9 genre codes (multi-genre movies included) across 1949–2019 |
| Movie listing switches between accessible cluster and list views without changing offset | ✅ done | `MovieViewToggle.svelte` (inlined `view_cozy`/`view_list` SVGs); `view` query param carried through pager/filter links; `MovieViewToggle.test.ts`, `movies/page.test.ts` (incl. an in-place-store-update regression test), extended `MovieListView.test.ts`. Live-verified: toggling persists across pagination and at mobile width (375px) against the real seeded stack |
| Movie credits and People rows display photos/fallbacks; unavailable People references degrade gracefully | ☐ not started | v1 batched hydration exists; photo-forward UI does not |
| Comments are validated, persisted, reverse-chronological, movie-owned, visible after submission | ☐ not started | no `movie_comment` migration yet |
| Credit/view icons have accessible names; movie deletion remains an explicit labeled confirmation | ☐ not started | depends on icon wiring above |
| Updated unit, PostgreSQL, MinIO, GraphQL, gRPC, frontend, E2E, and Compose smoke tests pass | 🟡 partial | MinIO-specific tests pass; remaining v2 areas untested because unbuilt |

Update this table (not the requirements doc) as each [plans/v2](../plans/v2/README.md)
task lands, with the specific test/file evidence like [v1-acceptance.md](v1-acceptance.md) does.
