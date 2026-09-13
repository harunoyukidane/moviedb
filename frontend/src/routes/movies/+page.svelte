<script lang="ts">
  import type { PageData } from './$types';
  import StateBanner from '$lib/components/StateBanner.svelte';
  import SearchBox from '$lib/components/SearchBox.svelte';

  export let data: PageData;

  $: page = data.page;
  $: hasPrev = page.offset > 0;
  $: hasNext = page.offset + page.limit < page.total;
  $: prevOffset = Math.max(0, page.offset - page.limit);
  $: nextOffset = page.offset + page.limit;
</script>

<div class="head-row">
  <h1>Movies</h1>
  <a class="new-link" href="/movies/new">+ New movie</a>
</div>

<SearchBox />

{#if data.error}
  <StateBanner variant="error">{data.error}</StateBanner>
{:else if page.items.length === 0}
  <StateBanner variant="info">No movies yet. Create your first one to get started.</StateBanner>
{:else}
  <ul class="poster-grid" aria-label="Movies">
    {#each page.items as movie (movie.id)}
      <li>
        <a class="poster-card" href={`/movies/${movie.id}`}>
          {#if movie.artwork}
            <img src={movie.artwork.url} alt={`Poster for ${movie.title}`} loading="lazy" />
          {:else}
            <div class="poster-fallback" aria-hidden="true">🎞️</div>
          {/if}
          <span class="poster-title" title={movie.title}>{movie.title}</span>
          {#if movie.releaseDate}<span class="poster-year">{movie.releaseDate.slice(0, 4)}</span>{/if}
        </a>
      </li>
    {/each}
  </ul>

  <nav class="pager" aria-label="Pagination">
    {#if hasPrev}<a href={`/movies?offset=${prevOffset}`}>← Previous</a>{/if}
    <span class="count">{page.offset + 1}–{Math.min(page.offset + page.limit, page.total)} of {page.total}</span>
    {#if hasNext}<a href={`/movies?offset=${nextOffset}`}>Next →</a>{/if}
  </nav>
{/if}

<style>
  .head-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--sp-2); }
  .new-link {
    background: var(--accent); color: var(--accent-contrast); padding: var(--sp-1) var(--sp-2);
    border-radius: var(--radius); font-weight: 600; text-decoration: none;
  }
  .poster-grid > li { display: flex; }
  .poster-card {
    display: flex; flex-direction: column; text-decoration: none; color: var(--text);
    background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius); overflow: hidden;
    width: 100%;
  }
  .poster-card img { width: 100%; aspect-ratio: 2 / 3; object-fit: cover; }
  .poster-fallback {
    width: 100%; aspect-ratio: 2 / 3; display: grid; place-items: center;
    font-size: 2.5rem; background: var(--surface-2);
  }
  .poster-title {
    padding: var(--sp-1) var(--sp-1) 0;
    font-weight: 600;
    /* Fixed two-line block so 1- and 2-line titles occupy the same height and
       cards align; titles longer than two lines are truncated with an ellipsis. */
    line-height: 1.25;
    min-height: calc(2 * 1.25em);
    display: -webkit-box;
    -webkit-line-clamp: 2;
    line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
  .poster-year { padding: 4px var(--sp-1) var(--sp-1); color: var(--text-muted); font-size: 0.875rem; margin-top: auto; }
  .pager { display: flex; gap: var(--sp-2); align-items: center; justify-content: center; margin-top: var(--sp-3); }
  .pager .count { color: var(--text-muted); }
</style>
