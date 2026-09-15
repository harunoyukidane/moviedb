<script lang="ts">
  import type { GenreCode } from '$lib/server/types';

  export let genres: GenreCode[] = [];
  export let years: number[] = [];
  export let selectedGenre: string | null = null;
  export let selectedYear: number | null = null;
  /** Carried through as a hidden field so switching genre/year doesn't reset the view preference. */
  export let view: 'cluster' | 'list' = 'cluster';

  let form: HTMLFormElement;

  // Progressive enhancement: auto-submit on change. The plain GET <form>
  // (no `offset` field) still works without JS, and always lands on offset=0.
  function submitNow() {
    form?.requestSubmit();
  }

  $: hasActiveFilter = !!selectedGenre || selectedYear != null;
</script>

<form bind:this={form} method="GET" class="movie-filters" aria-label="Filter movies">
  <label for="filter-genre">
    Genre
    <select id="filter-genre" name="genreCode" on:change={submitNow}>
      <option value="">All genres</option>
      {#each genres as g (g.code)}
        <option value={g.code} selected={g.code === selectedGenre}>{g.title}</option>
      {/each}
    </select>
  </label>

  <label for="filter-year">
    Release year
    <select id="filter-year" name="releaseYear" on:change={submitNow}>
      <option value="">All years</option>
      {#each years as y (y)}
        <option value={y} selected={y === selectedYear}>{y}</option>
      {/each}
    </select>
  </label>

  {#if view !== 'cluster'}
    <input type="hidden" name="view" value={view} />
  {/if}

  <button type="submit">Apply filters</button>
  {#if hasActiveFilter}
    <a class="clear" href={view !== 'cluster' ? `/movies?view=${view}` : '/movies'}>Clear filters</a>
  {/if}
</form>

<style>
  .movie-filters {
    display: flex;
    flex-wrap: wrap;
    align-items: end;
    gap: var(--sp-2);
    margin-bottom: var(--sp-2);
  }
  .movie-filters label {
    display: flex;
    flex-direction: column;
    gap: 4px;
    font-size: 0.875rem;
    color: var(--text-muted);
  }
  .movie-filters select {
    min-width: 10rem;
  }
  .movie-filters button {
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    padding: var(--sp-1) var(--sp-2);
    font-weight: 600;
  }
  .movie-filters .clear {
    align-self: center;
    color: var(--text-muted);
  }
</style>
