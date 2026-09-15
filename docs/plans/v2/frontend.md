# V2 frontend: views, credits, photos, icons

```yaml
status: current
canonical_for: v2-frontend-plan
last_verified: 2026-09-15
```

Status: 🟡 in progress — V2-08/V2-09 done; V2-10/V2-11/V2-12 remain (requirement 2
complete). The icon SVG assets themselves already exist under
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

## V2-10: Movie cast/crew presentation

- Extract `CreditPersonRow` and cast/crew section components.
- Show photo left; name and character/role title right.
- Use person-photo proxy URLs and the shared placeholder.
- Keep cast ordered by billing order with deterministic null/tie handling.
- Render unavailable People references explicitly without failing the movie page.
- Confirm person hydration remains one batched gRPC operation per request; add regression coverage.

## V2-11: People listing photos

- Extend the People list projection/GraphQL shape with a nullable same-origin `photoUrl` (or sufficient profile-photo state for the BFF to construct it).
- Render a linked person row with photo left and name right.
- Preserve search, pagination, total count, missing-photo fallback, and keyboard navigation.
- Add People contract, GraphQL, route, and component tests.

## V2-12: Action icons

- Introduce a small accessible icon-control component or a consistent inline-SVG pattern.
- Replace credit edit/delete text controls where required, retaining explicit accessible names/tooltips where useful.
- Keep movie deletion a labeled destructive confirmation, never an icon-only action.
- Verify focus return, confirmation behavior, contrast, target size, and screen-reader names.
