<script lang="ts">
  import type { PageData } from './$types';
  import { page } from '$app/stores';
  import StateBanner from '$lib/components/StateBanner.svelte';
  import MovieListToolbar from '$lib/features/movies/MovieListToolbar.svelte';
  import MovieViewToggle from '$lib/features/movies/MovieViewToggle.svelte';
  import MovieClusterView from '$lib/features/movies/MovieClusterView.svelte';
  import MovieListView from '$lib/features/movies/MovieListView.svelte';

  export let data: PageData;

  $: pageData = data.page;
  $: hasPrev = pageData.offset > 0;
  $: hasNext = pageData.offset + pageData.limit < pageData.total;
  $: prevOffset = Math.max(0, pageData.offset - pageData.limit);
  $: nextOffset = pageData.offset + pageData.limit;
  $: hasActiveFilter = !!data.filter.genreCode || data.filter.releaseYear != null;

  // The view preference is presentation-only (it never changes what is fetched), so it
  // lives in the `view` query param and is read directly from the URL rather than the
  // load function's data — this keeps SSR deterministic without adding a dependency
  // that would make the load function re-run just because the view changed.
  $: view = ($page.url.searchParams.get('view') === 'list' ? 'list' : 'cluster') as 'cluster' | 'list';

  // Every navigational href on this page (pager, view toggle) is built from the same
  // base params so switching one preference never drops another, and pagination/view
  // changes never reset each other or the active filters.
  //
  // Every true input is passed as an explicit argument (never read via closure) and
  // referenced by name in each `$:` statement below. Svelte's reactivity tracking is
  // purely syntactic per-statement — it cannot see that a called function internally
  // reads `view`/`filter`, so hiding them behind a no-arg lookup would leave these
  // hrefs stale after a client-side navigation that changes only that value.
  function buildMovieHref(
    offset: number,
    targetView: 'cluster' | 'list',
    filter: { genreCode: string | null; releaseYear: number | null }
  ): string {
    const params = new URLSearchParams();
    if (filter.genreCode) params.set('genreCode', filter.genreCode);
    if (filter.releaseYear != null) params.set('releaseYear', String(filter.releaseYear));
    if (targetView !== 'cluster') params.set('view', targetView);
    if (offset > 0) params.set('offset', String(offset));
    const qs = params.toString();
    return qs ? `/movies?${qs}` : '/movies';
  }

  $: prevHref = buildMovieHref(prevOffset, view, data.filter);
  $: nextHref = buildMovieHref(nextOffset, view, data.filter);
  $: clusterHref = buildMovieHref(pageData.offset, 'cluster', data.filter);
  $: listHref = buildMovieHref(pageData.offset, 'list', data.filter);
</script>

<div class="head-row">
  <h1>Movies</h1>
  <a class="new-link" href="/movies/new">+ New movie</a>
</div>

<div class="toolbar-row">
  <MovieListToolbar
    genres={data.genres}
    years={data.years}
    selectedGenre={data.filter.genreCode}
    selectedYear={data.filter.releaseYear}
    {view}
  />
  <MovieViewToggle {view} {clusterHref} {listHref} />
</div>

{#if data.error}
  <StateBanner variant="error">{data.error}</StateBanner>
{:else if pageData.items.length === 0}
  <StateBanner variant="info">
    {#if hasActiveFilter}
      No movies match the selected filters.
    {:else}
      No movies yet. Create your first one to get started.
    {/if}
  </StateBanner>
{:else}
  {#if view === 'list'}
    <MovieListView items={pageData.items} />
  {:else}
    <MovieClusterView items={pageData.items} />
  {/if}

  <nav class="pager" aria-label="Pagination">
    {#if hasPrev}<a href={prevHref}>← Previous</a>{/if}
    <span class="count">{pageData.offset + 1}–{Math.min(pageData.offset + pageData.limit, pageData.total)} of {pageData.total}</span>
    {#if hasNext}<a href={nextHref}>Next →</a>{/if}
  </nav>
{/if}

<style>
  .head-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--sp-2); }
  .new-link {
    background: var(--accent); color: var(--accent-contrast); padding: var(--sp-1) var(--sp-2);
    border-radius: var(--radius); font-weight: 600; text-decoration: none;
  }
  .toolbar-row { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--sp-2); flex-wrap: wrap; }
  .toolbar-row > :global(.movie-list-toolbar) { flex: 1 1 auto; }
  .pager { display: flex; gap: var(--sp-2); align-items: center; justify-content: center; margin-top: var(--sp-3); }
  .pager .count { color: var(--text-muted); }
</style>
