<script lang="ts">
  import type { PageData } from './$types';
  import CreditSection from '$lib/features/credits/CreditSection.svelte';

  export let data: PageData;

  $: movie = data.movie;
  let tab: 'cast' | 'creators' = 'cast';
</script>

<a class="back" href="/movies">← All movies</a>

<div class="detail">
  <section class="artwork-section" aria-label="Artwork">
    {#if movie.artwork}
      <img src={movie.artwork.url} alt={`Poster for ${movie.title}`} />
    {:else}
      <div class="poster-fallback" aria-hidden="true">🎞️</div>
    {/if}
  </section>

  <section class="info-section">
    <div class="title-row">
      <h1>{movie.title}</h1>
      <a class="btn" href={`/movies/${movie.id}/edit`}>Edit</a>
    </div>
    {#if movie.originalTitle}<p class="original">{movie.originalTitle}</p>{/if}

    <dl class="meta">
      {#if movie.releaseDate}<dt>Released</dt><dd>{movie.releaseDate}</dd>{/if}
      {#if movie.runtimeMinutes}<dt>Runtime</dt><dd>{movie.runtimeMinutes} min</dd>{/if}
      {#if movie.originalLanguage}<dt>Language</dt><dd>{movie.originalLanguage}</dd>{/if}
    </dl>

    {#if movie.genres.length}
      <ul class="genre-tags" aria-label="Genres">
        {#each movie.genres as g (g.code)}<li class="tag">{g.title}</li>{/each}
      </ul>
    {/if}

    {#if movie.synopsis}<p class="synopsis">{movie.synopsis}</p>{/if}

    <div class="tabs" role="tablist" aria-label="Credits">
      <button role="tab" aria-selected={tab === 'cast'} class:active={tab === 'cast'} on:click={() => (tab = 'cast')}>
        Cast ({movie.cast.length})
      </button>
      <button role="tab" aria-selected={tab === 'creators'} class:active={tab === 'creators'} on:click={() => (tab = 'creators')}>
        Creators ({movie.creators.length})
      </button>
    </div>

    {#if tab === 'cast'}
      <div role="tabpanel">
        <CreditSection credits={movie.cast} emptyMessage="No cast yet. Add credits from the editor." />
      </div>
    {:else}
      <div role="tabpanel">
        <CreditSection credits={movie.creators} emptyMessage="No creators yet. Add credits from the editor." />
      </div>
    {/if}
  </section>
</div>

<style>
  .back { display: inline-block; margin-bottom: var(--sp-2); color: var(--text-muted); }
  .detail { display: grid; grid-template-columns: 1fr; gap: var(--sp-3); }
  @media (min-width: 720px) { .detail { grid-template-columns: 280px 1fr; } }
  .artwork-section img, .poster-fallback { width: 100%; aspect-ratio: 2/3; object-fit: cover; border-radius: var(--radius); }
  .poster-fallback { display: grid; place-items: center; font-size: 3rem; background: var(--surface-2); }
  .title-row { display: flex; justify-content: space-between; align-items: flex-start; gap: var(--sp-2); }
  .btn { background: var(--surface-2); border: 1px solid var(--border); color: var(--text); padding: var(--sp-1) var(--sp-2); border-radius: var(--radius); text-decoration: none; }
  .original { color: var(--text-muted); margin-top: 0; }
  .meta { display: grid; grid-template-columns: auto 1fr; gap: 4px var(--sp-2); }
  .meta dt { color: var(--text-muted); }
  .meta dd { margin: 0; }
  .genre-tags { list-style: none; padding: 0; display: flex; flex-wrap: wrap; gap: var(--sp-1); margin: var(--sp-2) 0; }
  .tag { background: var(--surface-2); border: 1px solid var(--border); border-radius: 999px; padding: 2px var(--sp-1); font-size: 0.875rem; }
  .tabs { display: flex; gap: var(--sp-1); margin: var(--sp-2) 0; }
  .tabs button.active { background: var(--accent); color: var(--accent-contrast); border-color: var(--accent); }
</style>
