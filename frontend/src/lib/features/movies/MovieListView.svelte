<script lang="ts">
  import type { Movie } from '$lib/server/types';
  import MoviePosterFallback from './MoviePosterFallback.svelte';

  export let items: Movie[] = [];

  // The first several rows render above the fold - those images must not be
  // lazy-loaded, or the LCP candidate (almost always one of them) gets
  // deprioritized instead of helped.
  const EAGER_COUNT = 6;
</script>

<ul class="movie-rows" aria-label="Movies">
  {#each items as movie, i (movie.id)}
    <li>
      <a class="movie-row" href={`/movies/${movie.id}`}>
        <span class="row-poster">
          {#if movie.artwork}
            <img
              src={movie.artwork.url}
              alt={`Poster for ${movie.title}`}
              loading={i < EAGER_COUNT ? undefined : 'lazy'}
              fetchpriority={i === 0 ? 'high' : undefined}
            />
          {:else}
            <MoviePosterFallback />
          {/if}
        </span>
        <span class="row-body">
          <span class="row-heading">
            <span class="row-title text-truncate">{movie.title}</span>
          </span>
          {#if movie.releaseDate}
            <span class="row-year">{movie.releaseDate.slice(0, 4)}</span>
          {/if}

          {#if movie.genres.length}
            <span class="row-genres">{movie.genres.map((g) => g.title).join(', ')}</span>
          {/if}
          
          {#if movie.synopsis}
            <p class="row-synopsis">{movie.synopsis}</p>
          {/if}
        </span>
      </a>
    </li>
  {/each}
</ul>

<style>
  .movie-rows {
    display: flex;
    flex-direction: column;
    gap: var(--sp-2);
    list-style: none;
    margin: 0;
    padding: 0;
  }
  .movie-row {
    display: flex;
    align-items: flex-start;
    gap: var(--sp-2);
    text-decoration: none;
    color: var(--text);
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    padding: var(--sp-2);
  }
  .row-poster {
    flex: 0 0 auto;
    width: 100px;
  }
  .row-poster img,
  .row-poster :global(.poster-fallback) {
    width: 100px;
    aspect-ratio: 2 / 3;
    object-fit: cover;
    border-radius: calc(var(--radius) / 2);
  }
  .row-poster :global(.poster-fallback) {
    font-size: 1.75rem;
  }
  .row-body {
    display: flex;
    flex-direction: column;
    min-width: 0;
    gap: 2px;
  }
  .row-heading {
    display: flex;
    align-items: baseline;
    gap: var(--sp-1);
    flex-wrap: wrap;
  }
  .row-title {
    font-weight: 600;
  }
  .row-year {
    color: var(--text-muted);
    font-size: 0.875rem;
  }
  .row-genres {
    color: var(--text-muted);
    font-size: 0.8125rem;
  }
  .row-synopsis {
    margin: 0;
    color: var(--text-muted);
    font-size: 0.875rem;
    line-height: 1.35;
    /* Truncate to two lines regardless of content length. */
    display: -webkit-box;
    -webkit-line-clamp: 3;
    line-clamp: 3;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }

  @media (max-width: 480px) {
    .row-poster {
      width: 72px;
    }
    .row-poster img,
    .row-poster :global(.poster-fallback) {
      width: 72px;
    }
    .row-synopsis {
      -webkit-line-clamp: 1;
      line-clamp: 1;
    }
  }
</style>
