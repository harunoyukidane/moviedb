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
| Genre/year filters compose correctly, reset offset, preserve accurate totals | ☐ not started | no `MovieFilterInput` in `schema.graphqls` yet |
| Movie listing switches between accessible cluster and list views without changing offset | ☐ not started | icon SVGs present under `frontend/src/resources/`; no `$lib/features/movies` view toggle yet |
| Movie credits and People rows display photos/fallbacks; unavailable People references degrade gracefully | ☐ not started | v1 batched hydration exists; photo-forward UI does not |
| Comments are validated, persisted, reverse-chronological, movie-owned, visible after submission | ☐ not started | no `movie_comment` migration yet |
| Credit/view icons have accessible names; movie deletion remains an explicit labeled confirmation | ☐ not started | depends on icon wiring above |
| Updated unit, PostgreSQL, MinIO, GraphQL, gRPC, frontend, E2E, and Compose smoke tests pass | 🟡 partial | MinIO-specific tests pass; remaining v2 areas untested because unbuilt |

Update this table (not the requirements doc) as each [plans/v2](../plans/v2/README.md)
task lands, with the specific test/file evidence like [v1-acceptance.md](v1-acceptance.md) does.
