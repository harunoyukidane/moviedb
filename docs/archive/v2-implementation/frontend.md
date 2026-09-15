# V2 frontend: views, credits, photos, icons

```yaml
status: archived
canonical_for: none
last_verified: 2026-09-15
```

**Status: done.** Kept for the record of what was built and tested; see
[verification/v2-acceptance.md](../../../verification/v2-acceptance.md) for
evidence. Corresponds to [requirements.md](../../../product/requirements.md)
requirements 1, 2, 4, 5, and 6 — all now satisfied.

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

## V2-11: People listing photos — done

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

The `catalogue-service` rebuild needed to deploy this schema change required
a host Gradle build, which could not run in the authoring sandbox (same
`Unable to establish loopback connection` error seen for other backend
tasks, confirmed via both Bash and PowerShell — environment-level, not
tool-specific). The user ran `./gradlew :catalogue-service:bootJar` and
`docker compose up -d --build catalogue-service frontend` themselves; once
that was live, photos were confirmed rendering correctly for people with a
profile photo (real TMDB images) and the shared fallback for people without
one (verified via the accessibility tree — screenshot capture was flaky at
the time on this long, lazy-image-loading list, but the DOM/a11y evidence is
authoritative for correctness).

## V2-12: Action icons — done

Added `lib/components/Icon.svelte` (the one place mapping a short name to
path data from `frontend/src/resources/*.svg`, `fill="currentColor"`),
`IconButton.svelte` (real `<button>`, required `label` prop becomes both
`aria-label` and `title`, `danger` variant, 44×44px — WCAG 2.5.5 minimum
target size), and `IconLink.svelte` (the same pattern for a real `<a>`).
Refactored `MovieViewToggle` (V2-09) to use `Icon` instead of duplicated
inline SVG, for one consistent pattern app-wide.

Wired in three places:
- Movie/person detail "Edit" text buttons → `IconLink` (pencil icon,
  `label="Edit movie"`/`"Edit person"`), navigating to the same
  `/edit` routes as before.
- Per-credit "Remove from movie" text link → `IconButton` (trash icon,
  danger variant), same accessible name as before
  (`Remove {name} from movie}`) so the existing Playwright e2e assertion
  keeps working unchanged in substance.
- Movie/person deletion stayed a labeled text button + `ConfirmDialog` —
  deliberately never converted to an icon (requirement 1 criterion 4).

While verifying against `requirements.md` (Requirement 1) rather than just
the informal task bullets above, found the formal requirement also asked
for confirmation before a credit-removal icon actually removes the credit.
Clarified the intended design with the user in two rounds: credit mutations
(add/remove) live only behind the Edit icon on the Credit Editor — matching
the existing structure, where the detail page is already read-only and all
mutations already lived on `/edit`, with add + remove as the full credit
"editing" capability (no separate per-field edit dialog) — and, on
reflection, removing a credit does **not** need confirmation, since the
credited person's data is untouched and the credit can be re-added at any
time with no re-entry of data; confirmation is reserved for movie/person
deletion, which destroys substantial hand-entered data. (An intermediate
version of this change did add a `ConfirmDialog` for credit removal; it was
reverted per this follow-up decision — `journey.spec.ts`'s e2e step 6 is
back to a single click, no confirmation step, with a comment explaining
why.) `requirements.md` Requirement 1 criterion 3 is marked superseded
rather than silently dropped, so the decision stays visible.

Verified live end-to-end against the seeded stack: the delete icon and the
edit-navigation icons measure exactly 44×44px (`getBoundingClientRect`);
clicking the delete icon immediately removes the credit (no dialog), shows
the "Credit removed" banner, and moves focus to the "Add credit" button
rather than losing it to `<body>`; the edit icons on both movie and person
detail pages link to the correct `/edit` routes with accessible names
"Edit movie"/"Edit person"; movie/person deletion remain labeled buttons
with their own confirmation dialogs, unchanged.

## V2-15: Comment section UI — done

Added the Movie Detail page's comment section, closing the gap left after
the backend-only V2-13/V2-14 (`docs/archive/v2-implementation/catalogue.md`): `movie_comment`
persistence and the `comments`/`addMovieComment` GraphQL fields existed, but
nothing on the frontend called them. `lib/server/types.ts` gained
`MovieComment`/`MovieCommentPage`; `lib/server/operations.ts` gained
`listComments`/`addMovieComment`. `lib/features/comments/CommentSection.svelte`
(list rendering, modeled on `CreditSection.svelte`) renders comments in
exactly the order the API returns them (`createdAt DESC, id` tie-break,
§8.1) — deliberately not re-sorted client-side, same rationale as V2-10's
credit ordering. A new `lib/format.ts#formatDateTime` (first timestamp — as
opposed to date-only — formatting helper in the frontend) renders
`createdAt` via `<time datetime>`.

`movies/[id]/+page.server.ts` — previously read-only by explicit design
comment (all mutations live on `/edit`) — gained its first form action,
`addComment`: comments are additive/unmoderated rather than an "edit the
movie" concern, so they belong on the detail page itself rather than the
editor, and the existing read-only characterization is about movie-data
mutations, not this. The action trims `authorDisplayName`/`text` before
calling `addMovieComment`, mapping `BAD_USER_INPUT` to 400 and anything else
(including `NOT_FOUND` on a since-deleted movie) to 503, following the
`isValidationError(code) ? 400 : ...` convention used by `addCredit`/`update`.
`load` fetches the first page of comments (limit 10) alongside the movie,
degrading to an empty comment page (rather than failing the whole detail
page) if the comment fetch itself errors — mirroring how the movies list
degrades its genre-filter options. Pagination reuses the movies list's
offset-link pattern (`?commentsOffset=`, "← Newer"/"Older →", an "X–Y of Z"
count) rather than introducing a different UI idiom for one more paged list.

Covered by `CommentSection.test.ts` (empty state, custom empty message,
rendering, order preservation), `format.test.ts`, and
`movies/[id]/page.server.test.ts` (default/valid/invalid `commentsOffset`,
degraded comment fetch, trimming, validation-error and dependency-error
mapping on `addComment`).

Verified live against the seeded stack (`docker compose up -d --build
frontend`, after `npm run build` on the host per the Dockerfile — the
frontend image copies a pre-built `build/` directory, it does not build
inside the container): posting a comment trims the name/text, shows the
"Comment added." banner, clears the form, and the new comment appears
immediately with a server-generated, locale-formatted timestamp; the
comment persists across a full page reload; the native `required` attributes
block an empty submission before it reaches the server.
