<script lang="ts">
  import type { Movie } from '$lib/server/types';
  import MoviePosterFallback from './MoviePosterFallback.svelte';

  export let items: Movie[] = [];
</script>

<ul class="movie-rows" aria-label="Movies">
  {#each items as movie (movie.id)}
    <li>
      <a class="movie-row" href={`/movies/${movie.id}`}>
        <span class="row-poster">
          {#if movie.artwork}
            <img src={movie.artwork.url} alt={`Poster for ${movie.title}`} loading="lazy" />
          {:else}
            <MoviePosterFallback />
          {/if}
        </span>
        <span class="row-body">
          <span class="row-title">{movie.title}</span>
          {#if movie.releaseDate}<span class="row-year">{movie.releaseDate.slice(0, 4)}</span>{/if}
        </span>
      </a>
    </li>
  {/each}
</ul>

<style>
  .movie-rows {
    display: flex;
    flex-direction: column;
    gap: var(--sp-1);
  }
  .movie-row {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
    text-decoration: none;
    color: var(--text);
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    padding: var(--sp-1);
  }
  .row-poster {
    flex: 0 0 auto;
    width: 3rem;
  }
  .row-poster img,
  .row-poster :global(.poster-fallback) {
    width: 3rem;
    aspect-ratio: 2 / 3;
    object-fit: cover;
    border-radius: calc(var(--radius) / 2);
  }
  .row-poster :global(.poster-fallback) {
    font-size: 1.25rem;
  }
  .row-body {
    display: flex;
    flex-direction: column;
    min-width: 0;
  }
  .row-title {
    font-weight: 600;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .row-year {
    color: var(--text-muted);
    font-size: 0.875rem;
  }
</style>
