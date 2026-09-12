<script lang="ts">
  import type { PageData, ActionData } from './$types';
  import { enhance } from '$app/forms';
  import StateBanner from '$lib/components/StateBanner.svelte';
  import ConfirmDialog from '$lib/components/ConfirmDialog.svelte';
  import type { MovieCredit } from '$lib/server/types';

  export let data: PageData;
  export let form: ActionData;

  $: movie = data.movie;
  let tab: 'cast' | 'creators' = 'cast';
  let confirmDeleteOpen = false;
  let deleting = false;

  function submitDelete() {
    deleting = true;
    const f = document.getElementById('delete-form');
    if (f instanceof HTMLFormElement) f.requestSubmit();
  }

  function creditLine(c: MovieCredit): string {
    const who = c.person.available ? c.person.name : 'Unknown person';
    return c.category === 'CAST' ? `${who} as ${c.characterName}` : `${who} — ${c.role.title}`;
  }
</script>

<a class="back" href="/movies">← All movies</a>

<div class="detail">
  <section class="artwork-section" aria-label="Artwork">
    {#if movie.artwork}
      <img src={movie.artwork.url} alt={`Poster for ${movie.title}`} />
    {:else}
      <div class="poster-fallback" aria-hidden="true">🎞️</div>
    {/if}
    <a class="edit-art" href={`/movies/${movie.id}/edit#artwork`}>Manage artwork</a>
  </section>

  <section class="info-section">
    <div class="title-row">
      <h1>{movie.title}</h1>
      <div class="title-actions">
        <a class="btn" href={`/movies/${movie.id}/edit`}>Edit</a>
        <button type="button" class="danger" on:click={() => (confirmDeleteOpen = true)}>Delete</button>
      </div>
    </div>
    {#if movie.originalTitle}<p class="original">{movie.originalTitle}</p>{/if}

    {#if form?.message}
      <StateBanner variant="error">{form.message}</StateBanner>
    {/if}
    {#if form?.removed}
      <StateBanner variant="info">Removed from movie.</StateBanner>
    {/if}

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

    {#each [{ key: 'cast', items: movie.cast }, { key: 'creators', items: movie.creators }] as group}
      {#if tab === group.key}
        <div role="tabpanel">
          {#if group.items.length === 0}
            <StateBanner variant="info">No {group.key} yet. Add credits from the editor.</StateBanner>
          {:else}
            <ul class="credit-list">
              {#each group.items as c (c.id)}
                <li>
                  <span class:unavailable={!c.person.available}>{creditLine(c)}</span>
                  <form method="POST" action="?/removeCredit" use:enhance>
                    <input type="hidden" name="creditId" value={c.id} />
                    <button type="submit" class="link-danger" aria-label={`Remove ${c.person.name} from movie`}>
                      Remove from movie
                    </button>
                  </form>
                </li>
              {/each}
            </ul>
          {/if}
        </div>
      {/if}
    {/each}
  </section>
</div>

<ConfirmDialog
  bind:open={confirmDeleteOpen}
  title="Delete this movie?"
  confirmLabel="Delete movie"
  danger
  on:confirm={submitDelete}
>
  This permanently removes “{movie.title}” and its credits, genres, and artwork. This cannot be undone.
</ConfirmDialog>

<form id="delete-form" method="POST" action="?/delete" use:enhance hidden></form>

<style>
  .back { display: inline-block; margin-bottom: var(--sp-2); color: var(--text-muted); }
  .detail { display: grid; grid-template-columns: 1fr; gap: var(--sp-3); }
  @media (min-width: 720px) { .detail { grid-template-columns: 280px 1fr; } }
  .artwork-section img, .poster-fallback { width: 100%; aspect-ratio: 2/3; object-fit: cover; border-radius: var(--radius); }
  .poster-fallback { display: grid; place-items: center; font-size: 3rem; background: var(--surface-2); }
  .edit-art { display: inline-block; margin-top: var(--sp-1); }
  .title-row { display: flex; justify-content: space-between; align-items: flex-start; gap: var(--sp-2); }
  .title-actions { display: flex; gap: var(--sp-1); }
  .btn { background: var(--surface-2); border: 1px solid var(--border); color: var(--text); padding: var(--sp-1) var(--sp-2); border-radius: var(--radius); text-decoration: none; }
  .original { color: var(--text-muted); margin-top: 0; }
  .meta { display: grid; grid-template-columns: auto 1fr; gap: 4px var(--sp-2); }
  .meta dt { color: var(--text-muted); }
  .meta dd { margin: 0; }
  .genre-tags { list-style: none; padding: 0; display: flex; flex-wrap: wrap; gap: var(--sp-1); margin: var(--sp-2) 0; }
  .tag { background: var(--surface-2); border: 1px solid var(--border); border-radius: 999px; padding: 2px var(--sp-1); font-size: 0.875rem; }
  .tabs { display: flex; gap: var(--sp-1); margin: var(--sp-2) 0; }
  .tabs button.active { background: var(--accent); color: var(--accent-contrast); border-color: var(--accent); }
  .credit-list { list-style: none; padding: 0; }
  .credit-list li { display: flex; justify-content: space-between; align-items: center; padding: var(--sp-1) 0; border-bottom: 1px solid var(--border); }
  .unavailable { color: var(--text-muted); font-style: italic; }
  .link-danger { background: none; border: none; color: var(--danger); text-decoration: underline; padding: 0; }
</style>
