<script lang="ts">
  import type { Movie } from '$lib/server/types';
  import MoviePosterFallback from './MoviePosterFallback.svelte';

  export let items: Movie[] = [];

  // The first rendered row must not be lazy-loaded, or the LCP candidate
  // (almost always one of the first row's posters) gets deprioritized instead
  // of helped. `loading` is a server-rendered attribute that the browser's
  // preload scanner acts on before hydration (V2.7-02), so this can't be a
  // client-measured value - it has to be known at SSR time. `.poster-grid`
  // caps at 6 columns wide (app.css), so 6 is desktop-correct; narrower
  // viewports render fewer than 6 per row and eagerly load one extra row's
  // worth there, which is wasted bandwidth but not a correctness problem.
  const EAGER_COUNT = 6;
</script>

<ul class="poster-grid" aria-label="Movies">
  {#each items as movie, i (movie.id)}
    <li>
      <a class="poster-card" href={`/movies/${movie.id}`}>
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
        <span class="poster-title" title={movie.title}>{movie.title}</span>
        {#if movie.releaseDate}<span class="poster-year">{movie.releaseDate.slice(0, 4)}</span>{/if}
      </a>
    </li>
  {/each}
</ul>

<style>
  .poster-card {
    display: flex;
    flex-direction: column;
    text-decoration: none;
    color: var(--text);
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    overflow: hidden;
    width: 100%;
  }
  .poster-card img {
    width: 100%;
    aspect-ratio: 2 / 3;
    object-fit: cover;
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
  .poster-year {
    padding: 4px var(--sp-1) var(--sp-1);
    color: var(--text-muted);
    font-size: 0.875rem;
    margin-top: auto;
  }
</style>
