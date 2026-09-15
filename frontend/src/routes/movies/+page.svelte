<script lang="ts">
  import type { PageData } from './$types';
  import StateBanner from '$lib/components/StateBanner.svelte';
  import MovieListToolbar from '$lib/features/movies/MovieListToolbar.svelte';
  import MovieClusterView from '$lib/features/movies/MovieClusterView.svelte';

  export let data: PageData;

  $: page = data.page;
  $: hasPrev = page.offset > 0;
  $: hasNext = page.offset + page.limit < page.total;
  $: prevOffset = Math.max(0, page.offset - page.limit);
  $: nextOffset = page.offset + page.limit;
  $: hasActiveFilter = !!data.filter.genreCode || data.filter.releaseYear != null;

  // Pagination must retain the active filters (offset resets to 0 on filter change instead).
  function pagerHref(offset: number): string {
    const params = new URLSearchParams();
    if (data.filter.genreCode) params.set('genreCode', data.filter.genreCode);
    if (data.filter.releaseYear != null) params.set('releaseYear', String(data.filter.releaseYear));
    if (offset > 0) params.set('offset', String(offset));
    const qs = params.toString();
    return qs ? `/movies?${qs}` : '/movies';
  }
</script>

<div class="head-row">
  <h1>Movies</h1>
  <a class="new-link" href="/movies/new">+ New movie</a>
</div>

<MovieListToolbar
  genres={data.genres}
  years={data.years}
  selectedGenre={data.filter.genreCode}
  selectedYear={data.filter.releaseYear}
/>

{#if data.error}
  <StateBanner variant="error">{data.error}</StateBanner>
{:else if page.items.length === 0}
  <StateBanner variant="info">
    {#if hasActiveFilter}
      No movies match the selected filters.
    {:else}
      No movies yet. Create your first one to get started.
    {/if}
  </StateBanner>
{:else}
  <MovieClusterView items={page.items} />

  <nav class="pager" aria-label="Pagination">
    {#if hasPrev}<a href={pagerHref(prevOffset)}>← Previous</a>{/if}
    <span class="count">{page.offset + 1}–{Math.min(page.offset + page.limit, page.total)} of {page.total}</span>
    {#if hasNext}<a href={pagerHref(nextOffset)}>Next →</a>{/if}
  </nav>
{/if}

<style>
  .head-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--sp-2); }
  .new-link {
    background: var(--accent); color: var(--accent-contrast); padding: var(--sp-1) var(--sp-2);
    border-radius: var(--radius); font-weight: 600; text-decoration: none;
  }
  .pager { display: flex; gap: var(--sp-2); align-items: center; justify-content: center; margin-top: var(--sp-3); }
  .pager .count { color: var(--text-muted); }
</style>
