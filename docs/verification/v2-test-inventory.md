# V2 test inventory

```yaml
status: current
canonical_for: v2-test-inventory
last_verified: 2026-09-17
```

Full enumeration of automated tests covering v2 work: MinIO-backed object storage,
movie genre/year filters and expanded seed data, movie comments, the cluster/list
view toggle, credit/person photo display, accessible credit/view icons, and the
delete/upload concurrency-race hardening found by the V2-16 load test. V1
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

## Movie edit form field loss on save (regression)

A real bug found in manual verification: saving the movie edit form after
changing genres blanked title, originalTitle, synopsis, releaseDate,
runtimeMinutes, and originalLanguage — only the genre selection survived.
Root cause was frontend-only: the details form's `use:enhance` callback
called SvelteKit's `update()` with no options, which defaults to `reset:
true` and triggers a native `HTMLFormElement.reset()` on success; the
plain `value={movie.title}`-bound inputs aren't `bind:value`d, so once the
native reset wiped them, Svelte's diffing (which compares against its own
last-rendered value, not the live DOM) saw no change on reload and never
re-populated them. Genres survived only because their selection lives in
`GenreMultiSelect`'s component-local state behind a keyed `{#each}` block —
a different, always-reapplied reactivity path, not because of anything
genre-specific. Fixed by passing `update({ reset: false })`.

`frontend/src/routes/movies/[id]/edit/page.svelte.test.ts`

| Test name | Category | What it verifies |
|---|---|---|
| `keeps title/originalTitle/synopsis/releaseDate/runtimeMinutes/originalLanguage/genres after a real save+reload` | Edge case | regression; mocks `$app/forms`'s `enhance` with SvelteKit's real `update()` reset-default semantics (native `form.reset()` unless `reset: false`) so it exercises the actual bug mechanism — confirmed to fail if the fix is reverted to a bare `update()` |

`backend/catalogue-service/src/test/kotlin/com/moviecatalogue/catalogue/graphql/CatalogueGraphQlIntegrationTest.kt` (Testcontainers, GraphQL)

| Test name | Category | What it verifies |
|---|---|---|
| `updateMovie via variables persists every field together, not just genres` | Integration | server-side companion to the frontend regression: a single variables-based `updateMovie` (the shape the real BFF sends, not the inline-literal shape every other test here uses) changing title, originalTitle, synopsis, releaseDate, runtimeMinutes, originalLanguage, and genreCodes together must persist all of them, with particular attention to `originalLanguage`/`language` resolving correctly alongside the genre change |

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

## Concurrency / delete-race hardening (V2-16)

See [loadtest-findings.md](loadtest-findings.md) for the full writeup: `demo/loadtest`
found that concurrent deletes of the same movie/person could surface as a raw
`INTERNAL_ERROR` instead of `NOT_FOUND`, and a concurrent delete during an
artwork upload could leak a bare HTTP 500. Both now re-check inside the
transaction (via the existing `@Version` column, or a re-check + FK-violation
catch for artwork) and translate the race to a clean not-found error.

`backend/catalogue-service/src/test/kotlin/com/moviecatalogue/catalogue/application/ApplicationUseCasesTest.kt` — `MovieUseCasesTest`

| Test name | Category | What it verifies |
|---|---|---|
| `deleteMovie deletes when present` | Unit | |
| `deleteMovie throws NotFound when absent` | Edge case | |
| `deleteMovie throws NotFound, not a raw exception, when lost to a concurrent delete` | Edge case | concurrency/optimistic-lock |

`backend/catalogue-service/src/test/kotlin/com/moviecatalogue/catalogue/artwork/ArtworkUseCasesTest.kt`

| Test name | Category | What it verifies |
|---|---|---|
| `upload throws NotFound when the movie is deleted concurrently mid-transaction` | Edge case | concurrency, in-transaction re-check via captured `TransactionCallback` |

`backend/people-service/src/test/kotlin/com/moviecatalogue/people/application/PeopleApplicationServiceTest.kt`

| Test name | Category | What it verifies |
|---|---|---|
| `deletePerson deletes when present` | Unit | |
| `deletePerson throws NotFound when absent` | Edge case | |
| `deletePerson throws NotFound, not a raw exception, when lost to a concurrent delete` | Edge case | concurrency/optimistic-lock |

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

## Validation & error handling (V2.2)

Closes the gaps recorded below (kept as a record of what was missing before
2026-09-17, from the review that produced
[plans/v2.2/README.md](../plans/v2.2/README.md)): date extremity, malformed
scalar coercion, Unicode/emoji/control-character handling in stored fields,
malformed-id and BFF status-code mapping, field-level error rendering,
character counting, concurrent-edit narrow masks, and comment-seed
idempotency. V2.2-01 through V2.2-13 are all implemented and tested; only
V2.2-10's frontend `CodeCombobox` generalization and V2.2-11's staleness
banner needed new component surfaces, listed below with everything else.

### Character-class and length screening (`TextRules`, `MovieRules`, `PersonRules`)

`backend/catalogue-service/src/test/kotlin/com/moviecatalogue/catalogue/domain/RulesTest.kt`

| Test name | Category | What it verifies |
|---|---|---|
| `normalizeSynopsis allows blank, trims, screens and bounds at 5000` | Unit | |
| `normalizeSynopsis allows newlines but rejects emoji` | Edge case | |
| `comment text and author accept emoji, unlike catalogue metadata` | Edge case | the emoji/comment vs. metadata policy split |
| `a zero-width joiner between two emoji survives the comment screen, but is rejected between letters` | Edge case | adjacency-scoped ZWJ rule |
| `TextRulesTest`: `rejects NUL, control characters, bidi overrides, and zero-width characters` | Edge case | |
| `TextRulesTest`: `allows newlines and tabs only when the policy permits them` | Unit | |
| `TextRulesTest`: `NFC-normalizes so decomposed and precomposed forms are treated alike` | Edge case | |
| `TextRulesTest`: `rejects emoji unless the policy allows it` | Edge case | |

`backend/people-service/src/test/kotlin/com/moviecatalogue/people/domain/PersonRulesTest.kt`

| Test name | Category | What it verifies |
|---|---|---|
| `normalizeBiography allows blank, trims, screens and bounds at 5000` | Unit | |
| `normalizeBiography allows newlines but rejects emoji, unlike a comment` | Edge case | |
| `normalizeName rejects emoji and control characters` | Edge case | |
| `PersonTextRulesTest`: `rejects NUL, control characters, bidi overrides, and zero-width characters` | Edge case | people-service's independent copy of the same screen |

### Malformed ids and IDs-as-NOT_FOUND (`Ids.kt`)

`backend/catalogue-service/src/test/kotlin/com/moviecatalogue/catalogue/graphql/IdsTest.kt`

| Test name | Category | What it verifies |
|---|---|---|
| `parseId parses a well-formed UUID` | Unit | |
| `parseId reports a malformed id as NOT_FOUND, naming the kind and the offending value` | Edge case | |

`backend/catalogue-service/src/test/kotlin/com/moviecatalogue/catalogue/graphql/CatalogueGraphQlIntegrationTest.kt` (Testcontainers, GraphQL)

| Test name | Category | What it verifies |
|---|---|---|
| `a malformed movie, person, or credit id is NOT_FOUND, not INTERNAL_ERROR` | Edge case | end-to-end over all 18 GraphQL call sites via `parseId` |

### Country reference data (V2.2-10)

`backend/catalogue-service/src/test/kotlin/com/moviecatalogue/catalogue/graphql/CatalogueGraphQlIntegrationTest.kt`

| Test name | Category | What it verifies |
|---|---|---|
| `countryCodes query returns only active by default` | Integration | |
| `countryCodes query with activeOnly false includes inactive codes` | Edge case | |
| `createPerson accepts a birthCountryCode and Person resolves birthCountry` | Integration | end-to-end through People gRPC |

`backend/people-service/src/test/kotlin/com/moviecatalogue/people/application/PeopleApplicationServiceTest.kt`

| Test name | Category | What it verifies |
|---|---|---|
| `createPerson accepts an active birth country code` | Unit | |
| `createPerson rejects an unknown or inactive birth country code` | Edge case | |

`backend/people-service/src/test/kotlin/com/moviecatalogue/people/grpc/PeopleGrpcContractTest.kt`

| Test name | Category | What it verifies |
|---|---|---|
| `listCountries defaults to active only and maps every field` | Unit | |
| `listCountries with activeOnly false includes inactive` | Edge case | |

`frontend/src/lib/components/CodeCombobox.test.ts` (parametrized over language- and country-shaped data — 7 cases × 2 = 14 tests; generalized out of `LanguageSelect.test.ts`, which still passes unchanged against the `LanguageSelect` wrapper)

| Test name (per data set) | Category |
|---|---|
| `prefills the display name for an initially selected code` | Unit |
| `starts empty with no selection` | Unit |
| `filters suggestions by name as the user types` | Unit |
| `filters suggestions by code as well as name` | Unit |
| `picking a suggestion sets the hidden code input and closes the list` | Unit |
| `typing over a previous selection clears the hidden code until a new pick is made` | Unit |
| `clears unmatched typed text on blur so no fake selection lingers` | Unit |

`frontend/src/lib/components/CountrySelect.test.ts`

| Test name | Category |
|---|---|
| `renders with the default label/name/id and prefills a selected country` | Unit |

`frontend/src/routes/people/new/page.server.test.ts`, `frontend/src/routes/people/[id]/edit/page.server.test.ts`

| Test name | Category |
|---|---|
| `passes birthCountryCode through to the mutation` (both routes) | Unit |

### Concurrent-edit handling (V2.2-11)

`frontend/src/routes/movies/[id]/edit/page.server.test.ts`

| Test name | Category | What it verifies |
|---|---|---|
| `returns values on a backend failure so the form can re-render what the user typed (F16)` | Edge case | |
| `maps a dependency outage to 503, not 409 (F12)` | Edge case | |
| `maps a genuine conflict to 409` | Unit | |
| `only changed fields are sent in the update mask (F21)` | Edge case | narrow field mask |
| `a no-op submit (nothing changed) skips the mutation entirely` | Edge case | |

`frontend/src/routes/people/[id]/edit/page.server.test.ts`

| Test name | Category | What it verifies |
|---|---|---|
| `only changed fields are sent in the update mask (F21)` | Edge case | |
| `a no-op submit (nothing changed) skips the mutation entirely` | Edge case | |
| `returns values on a backend failure so the form can re-render what the user typed (F16/F22)` | Edge case | |

`backend/catalogue-service/src/test/kotlin/com/moviecatalogue/catalogue/graphql/CatalogueGraphQlIntegrationTest.kt`

| Test name | Category | What it verifies |
|---|---|---|
| `a conflict's message keeps version numbers out of the user-facing copy (F23)` | Edge case | |

`frontend/src/lib/components/StalenessBanner.test.ts`

| Test name | Category |
|---|---|
| `renders nothing when not visible` | Unit |
| `appears when the version has moved` | Unit |
| `is dismissible` | Unit |

`frontend/src/routes/movies/[id]/edit/page.svelte.test.ts`

| Test name | Category | What it verifies |
|---|---|---|
| `appears when the version has moved and is dismissible` | Edge case | full page wiring: window-focus/pre-submit check → banner |
| `does not appear when the version is unchanged` | Unit | |

### BFF error codes, statuses, and messages (V2.2-08)

`frontend/src/lib/errors.test.ts`

| Test name | Category |
|---|---|
| `STORAGE_UNAVAILABLE has its own message rather than falling through to the internal-error copy` | Edge case |

`frontend/src/lib/components/SearchBox.test.ts`

| Test name | Category |
|---|---|
| `clamps a long query to 100 characters client-side (F19) rather than sending it to the server` | Edge case |

`frontend/src/lib/components/CreditDialog.test.ts`

| Test name | Category |
|---|---|
| `shows an inline message when the person search request fails (F17)` | Edge case |

`frontend/src/routes/movies/[id]/edit/page.server.test.ts`

| Test name | Category | What it verifies |
|---|---|---|
| `the empty-file guard returns a specific message, not the generic banner (F13)` | Edge case | |
| `an upload failure maps each code to its own status, not 415 for everything (F11)` | Edge case | |
| `maps NOT_FOUND to 404, not 503 (F12)` | Edge case | delete action |

`frontend/src/routes/movies/[id]/page.server.test.ts`

| Test name | Category | What it verifies |
|---|---|---|
| `maps an unknown-movie failure to a 404, not 503 (F12)` | Edge case | renamed/corrected from a prior test that had encoded the wrong (pre-fix) behavior |
| `maps a dependency outage to a 503` | Edge case | |

### Client-side mirrors and character counters (V2.2-09, V2.2-12)

`frontend/src/lib/components/CharCounter.test.ts`

| Test name | Category |
|---|---|
| `counts in UTF-16 units so an emoji advances it by two` | Unit |
| `shows a neutral count well under the limit` | Unit |
| `applies a warning style within the last 10% of the limit` | Edge case |
| `applies an at-limit style and announces it politely instead of stopping input silently` | Edge case |
| `does not announce the limit when under it` | Unit |

`frontend/src/lib/components/DateField.test.ts`

| Test name | Category |
|---|---|
| `passes min and max through to the native input` | Unit |

`frontend/src/routes/error.svelte.test.ts`

| Test name | Category |
|---|---|
| `renders a 404 inside the app shell with the server message` | Edge case |
| `renders a 503 with the dependency-unavailable message` | Edge case |
| `links back to the movies list` | Unit |

### Comment seeding and idempotency (V2.2-13)

`backend/catalogue-service/src/test/kotlin/com/moviecatalogue/catalogue/application/ApplicationUseCasesTest.kt` — `CommentUseCasesTest`

| Test name | Category | What it verifies |
|---|---|---|
| `addComment with a new seedKey inserts and stamps it (V2_2-13)` | Unit | |
| `addComment with an existing seedKey upserts in place rather than inserting a duplicate` | Edge case | |

`backend/catalogue-service/src/test/kotlin/com/moviecatalogue/catalogue/graphql/CatalogueGraphQlIntegrationTest.kt`

| Test name | Category | What it verifies |
|---|---|---|
| `a seeded comment upserts by seedKey instead of duplicating on a second call` | Integration | |
| `an emoji comment, including a ZWJ sequence, round-trips through addMovieComment and comments` | Integration | via GraphQL variables, not inline literals, so no manual escaping risk |

`demo/importer/src/importer.test.ts`

| Test name | Category | What it verifies |
|---|---|---|
| `seeds the fixture comments for each movie` | Unit | |
| `a rerun produces identical comment counts and content` | Edge case | idempotency |
| `a movie with enough seeded comments spans more than one comment page` | Unit | the one fixture id (694) seeded past the 10/page pager |
| `a failed comment upsert does not fail the movie import` | Edge case | partial-failure isolation, matching the existing credit/photo handling |

## Summary counts

| Feature area | Unit | Edge case | Integration | E2E | Total |
|---|---:|---:|---:|---:|---:|
| Storage / MinIO | 8 | 56 | 26 | 0 | 90 |
| Catalogue filters & seed | 2 | 6 | 6 | 0 | 14 |
| Movie edit form field loss (regression) | 0 | 1 | 1 | 0 | 2 |
| Comments | 9 | 13 | 3 | 0 | 25 |
| Frontend view toggle & cluster view | 22 | 9 | 0 | 0 | 31 |
| Credits & photos | 11 | 10 | 3 | 0 | 24 |
| People photos | 5 | 3 | 0 | 0 | 8 |
| Concurrency / delete-race hardening | 2 | 5 | 0 | 0 | 7 |
| Icons / accessibility | 5 | 1 | 0 | 0 | 6 |
| Validation & error handling (V2.2) | 36 | 37 | 4 | 0 | 77 |
| E2E | 0 | 0 | 0 | 1 | 1 |
| **Total** | **100** | **141** | **43** | **1** | **285** |

Counts are per test method/case as enumerated above; a few tests could
reasonably sit in more than one category (e.g. a MinIO integration test that
also asserts an error path) and are counted once, under the category that
best matches the test's primary intent. The "Validation & error handling"
row's parametrized `CodeCombobox` cases (14) are counted individually, one per
data set, matching how the other rows count parametrized/table tests.
