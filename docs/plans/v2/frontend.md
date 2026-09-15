# V2 frontend: views, credits, photos, icons

```yaml
status: current
canonical_for: v2-frontend-plan
last_verified: 2026-09-15
```

Status: 🟡 in progress — V2-08 done; V2-09/V2-10/V2-11/V2-12 remain. The icon
SVG assets themselves already exist under `frontend/src/resources/`.
Corresponds to [requirements.md](../../product/requirements.md) requirements
1, 2, 4, and 6.

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

## V2-09: Cluster/list view switching

- Provide accessible cluster and list controls using the existing SVG resources.
- Render list rows with poster, title, release year, genres, and truncated synopsis.
- Switch without fetching a different page or changing the current offset.
- Default to cluster. Persist preference in a `view` query parameter or progressive-enhancement-safe local preference; query parameters are preferred for deterministic SSR.
- Add responsive behavior, placeholder behavior, accessible names, keyboard focus, and component tests.

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
