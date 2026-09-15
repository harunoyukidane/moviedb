# V2 test inventory

```yaml
status: current
canonical_for: v2-test-inventory
last_verified: 2026-09-15
```

Full enumeration of automated tests covering v2 work: MinIO-backed object storage,
movie genre/year filters and expanded seed data, movie comments, the cluster/list
view toggle, credit/person photo display, and accessible credit/view icons. V1
tests (plain CRUD, pre-v2 domain rules) are out of scope — see
[v1-acceptance.md](v1-acceptance.md) for that generation of tests.

This file is test-level detail: one row per test, per file. For
criterion-level pass/fail evidence and overall v2 status, see
[v2-acceptance.md](v2-acceptance.md). Test names below are taken verbatim (or
lightly cleaned up) from the source — Kotlin backtick-named `@Test fun`s and
Vitest/Playwright `it`/`test` descriptions already read as full sentences.

Categories: **Unit** (pure domain/rule logic, use-case orchestration with
mocks, frontend component logic), **Edge case** (boundary/negative/error
paths, missing/null data, empty states, degraded fallback,
concurrency/optimistic-lock, cascade delete, unavailable/404 references,
debounce/race conditions), **Integration** (real DB/GraphQL/gRPC/MinIO via
Testcontainers, or multi-module wiring), **E2E** (Playwright).

## Storage / MinIO (`backend/media`, artwork and photo store configuration)

### `backend/media/src/test/kotlin/com/moviecatalogue/media/MinioArtworkStoreTest.kt`

| Test name | Category | What it verifies |
|---|---|---|
| `put generates a UUID-shaped key with the sanitized extension` | Unit | |
| `put computes byte size and sha256 by digesting the stream, not trusting the SDK response` | Unit | |
| `put wraps a generic SDK failure as ArtworkStorageException without leaking details` | Edge case | |
| `listKeys wraps a mid-page listing failure as ArtworkStorageException` | Edge case | |
| `open rejects a traversal-style key before any backend call` | Edge case | path traversal |
| `open rejects a key containing a path separator` | Edge case | |
| `exists rejects a key with backslashes` | Edge case | |
| `delete rejects a blank key` | Edge case | |
| `delete rejects a key with an unexpected extension` | Edge case | |
| `open rejects a key with extra path-like segments appended to a valid uuid` | Edge case | |

### `backend/media/src/test/kotlin/com/moviecatalogue/media/MinioArtworkStoreIntegrationTest.kt` (Testcontainers MinIO)

| Test name | Category | What it verifies |
|---|---|---|
| `put, open, and exists round-trip bytes with the correct key shape, size, and digest` | Integration | |
| `unknown extension is dropped, persisting a bare-UUID key` | Integration | |
| `open and exists report unknown for a key that was never stored` | Edge case | |
| `delete returns true then false on repeat` | Edge case | idempotent delete |
| `delete of a never-stored key returns false without throwing` | Edge case | |
| `listKeys returns exactly the stored set after a put-delete mix, and empty when the bucket is empty` | Integration | |
| `listKeys fully drains pagination across many objects` | Edge case | pagination |
| `open, exists, and delete reject a traversal or slash-containing key even when a real object exists at that raw path` | Edge case | |
| `a multi-MiB payload round-trips correctly` | Integration | |
| `a real backend failure surfaces as ArtworkStorageException without leaking connection details` | Edge case | |

### `backend/media/src/test/kotlin/com/moviecatalogue/media/LocalArtworkStoreTest.kt`

| Test name | Category | What it verifies |
|---|---|---|
| `put generates a server key, hashes, and stores bytes` | Unit | local (non-MinIO) store baseline for comparison |
| `unknown extension is dropped (no unsafe extension persisted)` | Edge case | |
| `delete removes the file` | Unit | |
| `open returns null for unknown key` | Edge case | |
| `path traversal keys are rejected and never escape root` | Edge case | |
| `listKeys returns stored files and skips temp files` | Edge case | |

### `backend/media/src/test/kotlin/com/moviecatalogue/media/ImageContentValidatorTest.kt`

| Test name | Category | What it verifies |
|---|---|---|
| `accepts a real JPEG and reports dimensions` | Unit | |
| `accepts a real PNG` | Unit | |
| `rejects empty upload` | Edge case | |
| `rejects oversized upload with PAYLOAD_TOO_LARGE` | Edge case | |
| `rejects spoofed content - text with jpg intent` | Edge case | |
| `rejects truncated JPEG (valid signature, undecodable)` | Edge case | |
| `rejects GIF (not in allowlist)` | Edge case | |
| `rejects SVG (text-based, no binary image signature)` | Edge case | |
| `rejects a PNG signature followed by garbage (undecodable)` | Edge case | |

### `backend/media/src/test/kotlin/com/moviecatalogue/media/CrossBucketIsolationTest.kt` (Testcontainers MinIO)

| Test name | Category | What it verifies |
|---|---|---|
| `each service can fully operate on its own bucket` | Integration | catalogue/people buckets are independently usable |
| `a service's credentials cannot read, write, delete, or list the other service's bucket` | Edge case | per-service bucket credential isolation |

### Catalogue-service artwork tests

`backend/catalogue-service/src/test/kotlin/com/moviecatalogue/catalogue/artwork/ArtworkUseCasesTest.kt`

| Test name | Category | What it verifies |
|---|---|---|
| `db failure compensates by deleting the newly written file` | Edge case | upload-then-DB-failure compensation |
| `successful replace deletes old file only after commit` | Unit | |
| `old file delete throwing after commit does not fail the request` | Edge case | |
| `compensating delete throwing does not mask the original db failure` | Edge case | |

`backend/catalogue-service/src/test/kotlin/com/moviecatalogue/catalogue/artwork/ArtworkExceptionAdviceTest.kt`

| Test name | Category | What it verifies |
|---|---|---|
| `a storage backend outage while serving returns 503, not a default 500` | Edge case | |

`backend/catalogue-service/src/test/kotlin/com/moviecatalogue/catalogue/artwork/ArtworkOrphanSweeperTest.kt`

| Test name | Category | What it verifies |
|---|---|---|
| `a failed listing skips this run cleanly instead of propagating` | Edge case | |
| `one key failing to delete does not abort the rest of the sweep` | Edge case | |

`backend/catalogue-service/src/test/kotlin/com/moviecatalogue/catalogue/artwork/ArtworkStoreConfigurationTest.kt` (Testcontainers, 3 nested classes)

| Test name | Category | What it verifies |
|---|---|---|
| `ArtworkStoreLocalSelectionTest`: `defaults to the local filesystem store` | Integration | store-type selection wiring |
| `ArtworkStoreMinioSelectionTest`: `selects the MinIO-backed store when type is minio` | Integration | |
| `ArtworkStoreMinioSelectionTest`: `registers an UP readiness indicator for object storage` | Integration | health indicator |
| `ArtworkStoreFailFastTest`: `startup fails clearly when a required MinIO property is missing` | Edge case | |
| `ArtworkStoreFailFastTest`: `startup fails clearly when the configured bucket does not exist` | Edge case | |

`backend/catalogue-service/src/test/kotlin/com/moviecatalogue/catalogue/artwork/ArtworkHttpIntegrationTest.kt` (Testcontainers, local store)

| Test name | Category | What it verifies |
|---|---|---|
| `upload replace serve and delete a movie poster` | Integration | full HTTP lifecycle |
| `unknown artwork id returns 404` | Edge case | |
| `artwork metadata whose object is missing returns 404` | Edge case | |
| `spoofed content type jpg that is not an image is rejected` | Edge case | |
| `gif is rejected as unsupported` | Edge case | |
| `filename traversal never influences storage key` | Edge case | |
| `upload to unknown movie returns 404` | Edge case | |
| `orphan sweeper removes unreferenced files but keeps referenced ones` | Integration | |

`backend/catalogue-service/src/test/kotlin/com/moviecatalogue/catalogue/artwork/ArtworkMinioHttpIntegrationTest.kt` (Testcontainers MinIO)

| Test name | Category | What it verifies |
|---|---|---|
| `this context is really backed by MinIO, not the local filesystem` | Integration | test-setup sanity check |
| `upload serve and delete a movie poster through MinIO` | Integration | full HTTP lifecycle against real MinIO |
| `metadata with a missing MinIO object returns 404, not 500` | Edge case | |
| `a failed metadata swap compensates by deleting the newly uploaded MinIO object` | Edge case | |
| `replacing a poster removes the previously stored MinIO object` | Integration | |
| `orphan sweeper removes unreferenced MinIO objects but keeps referenced ones` | Integration | |

### People-service photo tests (mirror of catalogue artwork, for `Person.photoUrl`)

`backend/people-service/src/test/kotlin/com/moviecatalogue/people/photo/PersonPhotoUseCasesTest.kt`

| Test name | Category |
|---|---|
| `database failure compensates newly stored photo` | Edge case |
| `successful replace deletes old photo only after commit` | Unit |
| `old photo delete throwing after commit does not fail the request` | Edge case |
| `compensating delete throwing does not mask the original db failure` | Edge case |

`backend/people-service/src/test/kotlin/com/moviecatalogue/people/photo/PhotoExceptionAdviceTest.kt`

| Test name | Category |
|---|---|
| `a storage backend outage while serving returns 503, not a default 500` | Edge case |

`backend/people-service/src/test/kotlin/com/moviecatalogue/people/photo/PersonPhotoOrphanSweeperTest.kt`

| Test name | Category |
|---|---|
| `a failed listing skips this run cleanly instead of propagating` | Edge case |
| `one key failing to delete does not abort the rest of the sweep` | Edge case |

`backend/people-service/src/test/kotlin/com/moviecatalogue/people/photo/PhotoStoreConfigurationTest.kt` (Testcontainers, 3 nested classes)

| Test name | Category |
|---|---|
| `PhotoStoreLocalSelectionTest`: `defaults to the local filesystem store` | Integration |
| `PhotoStoreMinioSelectionTest`: `selects the MinIO-backed store when type is minio` | Integration |
| `PhotoStoreMinioSelectionTest`: `registers an UP readiness indicator for object storage` | Integration |
| `PhotoStoreFailFastTest`: `startup fails clearly when a required MinIO property is missing` | Edge case |
| `PhotoStoreFailFastTest`: `startup fails clearly when the configured bucket does not exist` | Edge case |

`backend/people-service/src/test/kotlin/com/moviecatalogue/people/photo/PersonPhotoHttpIntegrationTest.kt` (Testcontainers, local store)

| Test name | Category |
|---|---|
| `upload stores the photo key and serves it locally` | Integration |
| `validation is identical to movie path - spoofed content rejected` | Edge case |
| `replace removes the old uploaded file` | Integration |
| `delete removes photo and clears profile path` | Integration |
| `upload to unknown person returns 404` | Edge case |
| `serving a person with no uploaded photo returns 404` | Edge case |
| `serving metadata whose object is missing returns 404` | Edge case |
| `orphan sweeper removes unreferenced photo objects` | Integration |

`backend/people-service/src/test/kotlin/com/moviecatalogue/people/photo/PersonPhotoMinioHttpIntegrationTest.kt` (Testcontainers MinIO)

| Test name | Category |
|---|---|
| `this context is really backed by MinIO, not the local filesystem` | Integration |
| `upload stores the photo in MinIO and serves it back` | Integration |
| `metadata with a missing MinIO object returns 404, not 500` | Edge case |
| `a failed metadata swap compensates by deleting the newly uploaded MinIO object` | Edge case |
| `replace removes the old MinIO object` | Integration |
| `delete removes the photo from MinIO and clears profile path` | Integration |
| `orphan sweeper removes unreferenced MinIO photo objects` | Integration |

## Catalogue filters & seed (`backend/catalogue-service`)

`backend/catalogue-service/src/test/kotlin/com/moviecatalogue/catalogue/application/ApplicationUseCasesTest.kt` — `MovieUseCasesTest`

| Test name | Category | What it verifies |
|---|---|---|
| `listMovies with no filter queries with null genre and year` | Unit | |
| `listMovies validates genre code exists before querying` | Edge case | |
| `listMovies allows an existing but inactive genre code` | Edge case | |
| `listMovies rejects an out-of-range release year before querying` | Edge case | |
| `listMovies combines genre and year filters` | Unit | |

`backend/catalogue-service/src/test/kotlin/com/moviecatalogue/catalogue/persistence/CatalogueRepositoryIntegrationTest.kt` (Testcontainers Postgres)

| Test name | Category | What it verifies |
|---|---|---|
| `movie filter combines genre and year with AND semantics without duplicates` | Integration | |
| `reference code tables read seeded data with active and category filters` | Integration | |

`backend/catalogue-service/src/test/kotlin/com/moviecatalogue/catalogue/graphql/CatalogueGraphQlIntegrationTest.kt` (Testcontainers, GraphQL)

| Test name | Category | What it verifies |
|---|---|---|
| `movies filter combines genre and year, resets cleanly, and reports total` | Integration | |
| `movies filter with unknown genre code is BAD_USER_INPUT` | Edge case | |
| `movies filter with out-of-range release year is BAD_USER_INPUT` | Edge case | |
| `genres query returns only active by default` | Integration | |
| `page limit is clamped to 100` | Edge case | |

`backend/catalogue-service/src/test/kotlin/com/moviecatalogue/catalogue/schema/CatalogueSchemaIntegrationTest.kt` (Testcontainers Postgres, Flyway)

| Test name | Category | What it verifies |
|---|---|---|
| `seed reference data is present` | Integration | |
| `expanded genre seed (V2-07) makes genre and year filters demonstrable` | Integration | seed dataset spans enough genres/years |

## Comments

`backend/catalogue-service/src/test/kotlin/com/moviecatalogue/catalogue/domain/RulesTest.kt` — `MovieCommentRulesTest`

| Test name | Category |
|---|---|
| `normalizeAuthorDisplayName trims and validates` | Unit |
| `normalizeText trims and validates` | Unit |

`backend/catalogue-service/src/test/kotlin/com/moviecatalogue/catalogue/application/ApplicationUseCasesTest.kt` — `CommentUseCasesTest`

| Test name | Category | What it verifies |
|---|---|---|
| `addComment normalizes fields then inserts, validating the movie exists` | Unit | |
| `addComment rejects an unknown movie before validating or writing` | Edge case | |
| `addComment rejects blank author or text` | Edge case | |
| `listComments rejects an unknown movie` | Edge case | |
| `listComments clamps paging and maps the page` | Edge case | |

`backend/catalogue-service/src/test/kotlin/com/moviecatalogue/catalogue/persistence/CatalogueRepositoryIntegrationTest.kt` (Testcontainers Postgres)

| Test name | Category | What it verifies |
|---|---|---|
| `comment persists and pages in reverse chronological order with id tie-break` | Integration | ordering + pagination tie-break |

`backend/catalogue-service/src/test/kotlin/com/moviecatalogue/catalogue/graphql/CatalogueGraphQlIntegrationTest.kt` (Testcontainers, GraphQL)

| Test name | Category | What it verifies |
|---|---|---|
| `addMovieComment persists and trims fields, comments query returns it` | Integration | |
| `comments are returned in reverse chronological order with pagination` | Integration | |
| `blank author or text on a comment is BAD_USER_INPUT` | Edge case | |
| `over-limit author or text on a comment is BAD_USER_INPUT` | Edge case | |
| `commenting on or listing comments for a missing movie is NOT_FOUND` | Edge case | |
| `deleting a movie cascades to its comments` | Edge case | cascade delete |

### Frontend comments

`frontend/src/lib/features/comments/CommentSection.test.ts`

| Test name | Category |
|---|---|
| `shows the empty-state message when there are no comments` | Edge case |
| `supports a custom empty-state message` | Unit |
| `renders each comment with author and text` | Unit |
| `preserves the given comment order rather than re-sorting (server already orders reverse-chronologically)` | Unit |

`frontend/src/routes/movies/[id]/page.server.test.ts` — `/movies/[id] load` and `addComment action`

| Test name | Category | What it verifies |
|---|---|---|
| `loads the movie and the first page of comments by default` | Unit | |
| `passes through a valid commentsOffset` | Unit | |
| `ignores an invalid commentsOffset and falls back to 0` | Edge case | |
| `degrades to an empty comment page without failing the whole load when comments are unavailable` | Edge case | degraded fallback |
| `trims fields and adds the comment` | Unit | |
| `maps a validation failure to a 400 with a friendly message` | Edge case | |
| `maps an unknown-movie failure to a 503 with a friendly message` | Edge case | |

## Frontend view toggle & cluster view

`frontend/src/lib/features/movies/MovieViewToggle.test.ts`

| Test name | Category |
|---|---|
| `exposes two accessible, independently named links to each view` | Unit |
| `marks the active view with aria-current, leaving the other unmarked` | Unit |
| `preserves caller-supplied hrefs (e.g. carrying filter/offset params)` | Unit |

`frontend/src/lib/features/movies/MovieClusterView.test.ts`

| Test name | Category |
|---|---|
| `renders a poster card per movie, linking to the movie detail page` | Unit |
| `shows the release year when present` | Unit |
| `shows the shared poster fallback when a movie has no artwork` | Edge case |
| `renders nothing but an empty, labeled list for an empty page` | Edge case |

`frontend/src/lib/features/movies/MovieListView.test.ts`

| Test name | Category |
|---|---|
| `renders one row per movie, linking to the movie detail page` | Unit |
| `shows genres and a truncated synopsis` | Unit |
| `omits the genre line and synopsis paragraph when absent` | Edge case |
| `shows the shared poster fallback when a movie has no artwork` | Edge case |

`frontend/src/lib/features/movies/MovieListToolbar.test.ts`

| Test name | Category |
|---|---|
| `composes the search box and the movie filters together` | Unit |
| `forwards the current filter selection to MovieFilters` | Unit |

`frontend/src/lib/features/movies/MovieFilters.test.ts`

| Test name | Category |
|---|---|
| `renders an "All genres"/"All years" option plus each choice` | Unit |
| `preselects the current genre and year` | Unit |
| `hides the clear-filters link with no active filter` | Unit |
| `shows a clear-filters link back to /movies when a filter is active` | Unit |
| `carries the current view through as a hidden field and into "Clear filters"` | Unit |
| `omits the hidden view field for the default cluster view` | Edge case |
| `submits the form (GET, no offset field) as a real navigation when a select changes` | Unit |

`frontend/src/routes/movies/page.server.test.ts` — `/movies load`

| Test name | Category | What it verifies |
|---|---|---|
| `queries with no filter and offset 0 by default` | Unit | |
| `passes through a genreCode that matches a loaded genre` | Unit | |
| `ignores an unknown genreCode instead of erroring` | Edge case | |
| `passes through a valid releaseYear and combines with genreCode (AND)` | Unit | |
| `ignores a non-numeric or out-of-range releaseYear` | Edge case | |
| `retains filters across pagination via the offset param` | Unit | |
| `degrades to an empty genre list without failing the page when reference data is unavailable` | Edge case | degraded fallback |

`frontend/src/routes/movies/page.test.ts` — `/movies view toggle wiring`

| Test name | Category | What it verifies |
|---|---|---|
| `defaults to cluster view and points the list link at ?view=list` | Unit | |
| `renders the list view and points the cluster link back to /movies when ?view=list` | Unit | |
| `view links and pager links both carry the active genre/year filter` | Unit | |
| `recomputes the pager hrefs after the URL store updates in place (no remount)` | Edge case | in-place store update regression |

## Credits & photos (movie-detail credit rows)

`frontend/src/lib/features/credits/CreditPersonRow.test.ts`

| Test name | Category |
|---|---|
| `shows the photo, name, and role text for an available person` | Unit |
| `links an available person to their detail page` | Unit |
| `does not link an unavailable person (no valid detail page)` | Edge case |
| `falls back to the shared placeholder when the photo fails to load (e.g. 404, no photo)` | Edge case |
| `shows "Unknown person" and the shared placeholder for an unavailable person, without requesting a photo` | Edge case |
| `omits the role line when roleText is empty` | Edge case |

`frontend/src/lib/features/credits/CreditSection.test.ts`

| Test name | Category |
|---|---|
| `shows the empty-state message when there are no credits` | Edge case |
| `renders cast rows with the character name as role text` | Unit |
| `renders crew rows with the role title as role text` | Unit |
| `preserves the given credit order rather than re-sorting (server already orders by billing order)` | Unit |

`frontend/src/lib/features/credits/CreditSection.hydration.test.ts`

| Test name | Category |
|---|---|
| `renders many credit rows without making any fetch calls` | Unit |

`frontend/src/lib/server/operations.test.ts` — `getMovie`

| Test name | Category | What it verifies |
|---|---|---|
| `issues exactly one GraphQL request for the whole movie detail projection` | Unit | batched fetch, no N+1 |

`frontend/src/lib/components/CreditDialog.test.ts`

| Test name | Category |
|---|---|
| `is an accessible modal dialog` | Unit |
| `requires a character name for CAST roles before enabling submit` | Edge case |
| `CREW roles do not require a character name` | Unit |

### Backend credit read model (batched person hydration, photoUrl)

`backend/catalogue-service/src/test/kotlin/com/moviecatalogue/catalogue/application/ApplicationUseCasesTest.kt` — `PersonHydratorTest`

| Test name | Category | What it verifies |
|---|---|---|
| `resolves many credits with a single batched gRPC call` | Unit | batching |
| `degraded people service yields available false without failing` | Edge case | degraded fallback |
| `missing person marked unavailable` | Edge case | |

`backend/catalogue-service/src/test/kotlin/com/moviecatalogue/catalogue/graphql/MappersTest.kt` — `PersonDataMapperTest`

| Test name | Category |
|---|---|
| `photoUrl is the same-origin proxy path when a photo exists` | Unit |
| `photoUrl is null when the person has no photo` | Edge case |

`backend/catalogue-service/src/test/kotlin/com/moviecatalogue/catalogue/graphql/CatalogueGraphQlIntegrationTest.kt` (Testcontainers, GraphQL)

| Test name | Category | What it verifies |
|---|---|---|
| `movies list with many credits performs one batched gRPC call` | Integration | |
| `degraded people service yields available false without failing the page` | Edge case | |
| `person photoUrl is the same-origin proxy path when a photo exists, null otherwise` | Integration | |
| `people query lists people (blank query) and filters by query` | Integration | |

## People photos (people listing)

`frontend/src/lib/features/people/PersonListRow.test.ts`

| Test name | Category |
|---|---|
| `links to the person detail page and shows their name and dates` | Unit |
| `renders the photo when photoUrl is present` | Unit |
| `shows the shared fallback (no attempted image load) when photoUrl is absent` | Edge case |
| `omits the dates span when absent` | Edge case |

`frontend/src/routes/people/page.server.test.ts` — `/people load`

| Test name | Category |
|---|---|
| `passes through photoUrl on each item unchanged` | Unit |

`frontend/src/lib/server/media-proxy.test.ts` — `proxyMediaGet` (backs both movie artwork and person photo same-origin proxying)

| Test name | Category |
|---|---|
| `streams successful responses and preserves cache headers` | Unit |
| `returns a gateway error when the media service is unreachable` | Edge case |

`frontend/src/lib/format.test.ts` — `formatDateTime` (used for comment timestamps)

| Test name | Category |
|---|---|
| `formats an ISO-8601 offset date-time into a readable date and time` | Unit |

## Icons / accessibility

`frontend/src/lib/components/IconButton.test.ts`

| Test name | Category |
|---|---|
| `is a real button with an accessible name from the required label prop` | Unit |
| `defaults to type="button" so it never accidentally submits a form` | Edge case |
| `accepts type="submit" for use inside a per-row removal form` | Unit |
| `forwards click events to the caller` | Unit |
| `renders the danger variant distinctly` | Unit |

`frontend/src/lib/components/IconLink.test.ts`

| Test name | Category |
|---|---|
| `is a real link with an accessible name and matching tooltip` | Unit |

## E2E

`frontend/e2e/journey.spec.ts` (Playwright, against the composed stack + BFF)

| Test name | Category | What it verifies |
|---|---|---|
| `full catalogue journey through the BFF UI` | E2E | Single critical-path journey: create person, create movie, add a cast credit via the accessible `CreditDialog`, upload artwork, search finds both the movie and the person, remove the credit via the icon control (no confirmation, per the v2 design decision), delete the movie (labeled confirmation) |

## Summary counts

| Feature area | Unit | Edge case | Integration | E2E | Total |
|---|---:|---:|---:|---:|---:|
| Storage / MinIO | 8 | 56 | 26 | 0 | 90 |
| Catalogue filters & seed | 2 | 6 | 6 | 0 | 14 |
| Comments | 9 | 13 | 3 | 0 | 25 |
| Frontend view toggle & cluster view | 22 | 9 | 0 | 0 | 31 |
| Credits & photos | 11 | 10 | 3 | 0 | 24 |
| People photos | 5 | 3 | 0 | 0 | 8 |
| Icons / accessibility | 5 | 1 | 0 | 0 | 6 |
| E2E | 0 | 0 | 0 | 1 | 1 |
| **Total** | **62** | **98** | **38** | **1** | **199** |

Counts are per test method/case as enumerated above; a few tests could
reasonably sit in more than one category (e.g. a MinIO integration test that
also asserts an error path) and are counted once, under the category that
best matches the test's primary intent.
