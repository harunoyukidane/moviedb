# V2 frontend: views, credits, photos, icons

```yaml
status: current
canonical_for: v2-frontend-plan
last_verified: 2026-09-15
```

Status: 🟡 in progress — V2-08/V2-09/V2-10/V2-11 done (requirements 2, 4, and
6 complete; V2-11's backend rebuild/deploy is still pending, see its section
below); V2-12 remains. The icon SVG assets themselves already exist under
`frontend/src/resources/`. Corresponds to
[requirements.md](../../product/requirements.md) requirements 1, 2, 4, and 6.

## V2-08: Movie-list feature components — done

Extracted `lib/features/movies/MovieListToolbar.svelte` (composes the
existing `SearchBox` + `MovieFilters`), `MovieClusterView.svelte` (the poster
grid, moved verbatim out of `movies/+page.svelte`), `MovieListView.svelte`
(a new row-based renderer — poster/fallback, title, release year from
currently-loaded fields; genre badges and truncated synopsis are V2-09's job
once the toggle lands and the GraphQL query is extended), and
`MoviePosterFallback.svelte` (the shared 🎞️ placeholder, used by both
renderers). `movies/+page.svelte` now only owns URL/pagination state
(`pagerHref`, offset math) and composes these components; no backend calls
live in any renderer. `MovieListView` is not yet wired to a toggle — cluster
remains the only rendered view, unchanged from before this refactor.
Covered by `MovieClusterView.test.ts`, `MovieListView.test.ts`, and
`MovieListToolbar.test.ts`; full frontend suite green, `svelte-check` clean.

## V2-09: Cluster/list view switching — done

Added `MovieViewToggle.svelte` (inlines the existing `view_cozy`/`view_list`
SVGs from `frontend/src/resources/` with `fill="currentColor"` so they adapt
to the theme) as a pair of real `<a>` links — not JS-dispatched buttons —
consistent with the pager/"Clear filters" links elsewhere on this page: works
without JS, and a plain click never re-runs `load` because the `view` param
is never read by `+page.server.ts` (view is presentation-only; `+page.svelte`
reads it straight from `$page.url`). `MovieListView` now renders genres and
a 2-line-clamped truncated synopsis (`operations.ts#listMovies` extended to
select `synopsis`/`genres`). The preference lives in the `view` query
param, defaults to cluster, and is carried through every navigational link
on the page — pager prev/next, the toggle itself, and `MovieFilters`' hidden
field — so switching filters, paging, or the view never resets any of the
others or the current offset.

Caught and fixed a real bug via manual browser verification (not by the
initial unit tests): the pager/toggle hrefs were built by a plain function
that read `view`/`filter` via closure instead of as explicit arguments —
Svelte's reactivity tracking is per-statement and purely syntactic, so it
couldn't see that dependency, and a pure client-side navigation that changed
only `view` left those hrefs stale (a full page reload masked it, since that
always recomputes everything fresh). Fixed by passing every true input
explicitly to `$: hrefName = buildMovieHref(offset, view, filter)` style
statements. Added a regression test that updates the mounted component's
URL store in place (`page.test.ts`) rather than only rendering fresh per
case, since a fresh `render()` cannot reproduce this class of bug.

Covered by `MovieViewToggle.test.ts`, extended `MovieListView.test.ts`
(genres/synopsis), and `movies/page.test.ts` (default/list rendering,
aria-current, filter/offset carried through, the in-place-update regression
case). Manually verified end-to-end against the live seeded stack: toggling
persists across pagination, works at mobile width (375px, synopsis clamps to
one line), and keyboard/screen-reader semantics (`role="group"`,
`aria-current`, accessible link names) are correct.

## V2-10: Movie cast/crew presentation — done

Added `lib/features/credits/CreditPersonRow.svelte` (photo left, name +
character/role title right) and `CreditSection.svelte` (empty state +
maps a credit list to rows, in the given order), plus a shared
`lib/features/people/PersonPhotoFallback.svelte` (👤, ahead of V2-11's need
for the same thing). `movies/[id]/+page.svelte`'s cast/creators tab panels
now compose `CreditSection` instead of inline markup; the old
`creditLine()` string-building helper is gone. Photos are same-origin proxy
URLs built directly from `person.id` (`/api/people/{id}/photo}`, already
existing from v1) — no new GraphQL field, no extra request beyond the
browser's native `<img>` load; a 404 or an unavailable person both fall
back to the shared placeholder via `on:error`/a conditional, and an
unavailable person is labeled "Unknown person" without failing the page.
Credits are rendered in exactly the order the GraphQL API returns them
(cast by billing order, nulls last, id tie-break; crew by role/billing/id,
§8.1) — deliberately not re-sorted client-side, since that already-tested
ordering lives in `MovieReadService.credits()` and re-sorting here risks
silently diverging from it.

Regression coverage for "one batched gRPC operation per request": since
photos are plain `<img src>` (not `fetch`), `CreditSection.hydration.test.ts`
renders 8 credit rows and asserts zero `fetch` calls are made by the
component tree, and `operations.test.ts` asserts `getMovie` still issues
exactly one GraphQL request containing both `cast`/`creators` blocks (i.e.
no second query was introduced for photos). Also covered by
`CreditPersonRow.test.ts` and `CreditSection.test.ts` (photo/fallback
states, unavailable-person handling, order preservation). Manually verified
against the live seeded stack: cast/creator tabs render real TMDB photos,
and a person with no photo (e.g. "Beau Marks") shows the fallback cleanly.

## V2-11: People listing photos — done (backend rebuild pending)

Added a nullable `Person.photoUrl: String` to the GraphQL schema, computed in
`PersonData.toGql()` as the existing same-origin proxy path
(`/api/people/{id}/photo`) when `profilePath != null`, else `null` — mirrors
how `ArtworkAsset.toGql`'s `url` already embeds the frontend's own proxy
path for movie artwork. Unlike V2-10 (where the movie-detail credit list
just attempts the photo URL and falls back on `on:error`), the people
*listing* needed to know up front whether a photo exists, so a list of many
rows doesn't fire a 404 request per photo-less person. `operations.ts#listPeople`
selects `photoUrl`; `lib/features/people/PersonListRow.svelte` renders the
photo only when present (else the shared `PersonPhotoFallback`), name, and
optional birth/death-year text, all inside one link — search (`query` param,
still used by `/api/people-search` autocomplete), pagination, and total
count are all unchanged since only a field was added to the existing query.

Covered by `MappersTest.kt` (`photoUrl` computation), a
`CatalogueGraphQlIntegrationTest` case (`photoUrl` present/absent over
GraphQL), `PersonListRow.test.ts`, and `people/page.server.test.ts`.

**Not yet live-verified**: rebuilding `catalogue-service` requires a host
Gradle build (`./gradlew :catalogue-service:bootJar`), which fails in this
sandbox with the same `Unable to establish loopback connection` error seen
earlier for other backend tasks (confirmed via both Bash and PowerShell, so
it's environment-level, not tool-specific) — the Docker image only copies a
host-built jar, it doesn't build inside the container. The frontend
container was intentionally left on its pre-V2-11 build (which doesn't
query `photoUrl`) so the live `/people` page keeps working against the
currently-running, older backend. To finish verifying:
```
cd backend && ./gradlew :catalogue-service:bootJar
cd ../frontend && npm run build
cd .. && docker compose up -d --build catalogue-service frontend
```

## V2-12: Action icons

- Introduce a small accessible icon-control component or a consistent inline-SVG pattern.
- Replace credit edit/delete text controls where required, retaining explicit accessible names/tooltips where useful.
- Keep movie deletion a labeled destructive confirmation, never an icon-only action.
- Verify focus return, confirmation behavior, contrast, target size, and screen-reader names.
