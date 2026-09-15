<script lang="ts">
  import type { PageData, ActionData } from './$types';
  import { enhance } from '$app/forms';
  import StateBanner from '$lib/components/StateBanner.svelte';
  import GenreMultiSelect from '$lib/components/GenreMultiSelect.svelte';
  import ArtworkUpload from '$lib/components/ArtworkUpload.svelte';
  import CreditDialog from '$lib/components/CreditDialog.svelte';
  import ConfirmDialog from '$lib/components/ConfirmDialog.svelte';
  import IconButton from '$lib/components/IconButton.svelte';
  import type { MovieCredit } from '$lib/server/types';

  export let data: PageData;
  export let form: ActionData;

  $: movie = data.movie;
  let savingDetails = false;
  let creditOpen = false;
  let confirmDeleteOpen = false;
  let addCreditButton: HTMLButtonElement;
  let removeCreditForm: HTMLFormElement;
  let confirmRemoveOpen = false;
  let pendingRemoveCredit: MovieCredit | null = null;

  $: selectedGenres = movie.genres.map((g) => g.code);
  $: allCredits = [...movie.cast, ...movie.creators];

  function creditLine(c: MovieCredit): string {
    const who = c.person.available ? c.person.name : 'Unknown person';
    return c.category === 'CAST' ? `${who} as ${c.characterName}` : `${who} — ${c.role.title}`;
  }

  function submitDelete() {
    const f = document.getElementById('delete-movie-form');
    if (f instanceof HTMLFormElement) f.requestSubmit();
  }

  function confirmRemoveCredit(credit: MovieCredit) {
    pendingRemoveCredit = credit;
    confirmRemoveOpen = true;
  }

  function submitRemoveCredit() {
    removeCreditForm?.requestSubmit();
  }
</script>

<a class="back" href={`/movies/${movie.id}`}>← Back to movie</a>
<h1>Edit “{movie.title}”</h1>

{#if form?.updated}
  <StateBanner variant="info">Changes saved.</StateBanner>
{/if}

<section class="section" aria-label="Movie details">
  <h2>Details</h2>
  {#if form?.message && form?.section === 'details'}
    <StateBanner variant="error">{form.message}</StateBanner>
  {/if}
  <form
    method="POST"
    action="?/update"
    use:enhance={() => {
      savingDetails = true;
      return async ({ update }) => {
        await update();
        savingDetails = false;
      };
    }}
  >
    <input type="hidden" name="expectedVersion" value={movie.version} />
    <div class="field">
      <label for="title">Title *</label>
      <input id="title" name="title" required value={movie.title} />
    </div>
    <div class="field">
      <label for="originalTitle">Original title</label>
      <input id="originalTitle" name="originalTitle" value={movie.originalTitle ?? ''} />
    </div>
    <div class="field">
      <label for="synopsis">Synopsis</label>
      <textarea id="synopsis" name="synopsis" rows="4">{movie.synopsis}</textarea>
    </div>
    <div class="grid-2">
      <div class="field">
        <label for="releaseDate">Release date</label>
        <input id="releaseDate" name="releaseDate" type="date" value={movie.releaseDate ?? ''} />
      </div>
      <div class="field">
        <label for="runtimeMinutes">Runtime (min)</label>
        <input id="runtimeMinutes" name="runtimeMinutes" type="number" min="1" value={movie.runtimeMinutes ?? ''} />
      </div>
    </div>
    <div class="field">
      <label for="originalLanguage">Original language</label>
      <input id="originalLanguage" name="originalLanguage" maxlength="10" value={movie.originalLanguage ?? ''} />
    </div>
    <GenreMultiSelect genres={data.genres} selected={selectedGenres} />
    <button type="submit" class="primary" disabled={savingDetails}>
      {savingDetails ? 'Saving…' : 'Save changes'}
    </button>
  </form>
</section>

<section class="section" id="artwork" aria-label="Artwork">
  <h2>Artwork</h2>
  {#if form?.message && form?.section === 'artwork'}
    <StateBanner variant="error">{form.message}</StateBanner>
  {/if}
  {#if movie.artwork}
    <img class="current-art" src={movie.artwork.url} alt={`Current poster for ${movie.title}`} />
    <form method="POST" action="?/deleteArtwork" use:enhance>
      <button type="submit" class="danger">Remove artwork</button>
    </form>
  {:else}
    <p class="hint">No artwork yet.</p>
  {/if}
  <ArtworkUpload action="?/uploadArtwork" label={movie.artwork ? 'Replace artwork' : 'Upload artwork'} />
</section>

<section class="section" aria-label="Credits">
  <div class="credits-head">
    <h2>Credits</h2>
    <button type="button" class="primary" bind:this={addCreditButton} on:click={() => (creditOpen = true)}>
      Add credit
    </button>
  </div>
  {#if form?.creditAdded}
    <StateBanner variant="info">Credit added.</StateBanner>
  {/if}
  {#if form?.creditRemoved}
    <StateBanner variant="info">Credit removed from movie.</StateBanner>
  {/if}
  {#if form?.message && form?.section === 'credit'}
    <StateBanner variant="error">{form.message}</StateBanner>
  {/if}

  {#if allCredits.length === 0}
    <p class="hint">No credits yet. Use “Add credit” to attach cast or crew.</p>
  {:else}
    <ul class="credit-list">
      {#each allCredits as c (c.id)}
        <li>
          <span class:unavailable={!c.person.available}>
            <span class="cat-badge">{c.category}</span>
            {creditLine(c)}
          </span>
          <IconButton
            icon="delete"
            variant="danger"
            label={`Remove ${c.person.name} from movie`}
            on:click={() => confirmRemoveCredit(c)}
          />
        </li>
      {/each}
    </ul>
  {/if}
</section>

<section class="section danger-zone" aria-label="Danger zone">
  <h2>Danger zone</h2>
  {#if form?.message && form?.section === 'danger'}
    <StateBanner variant="error">{form.message}</StateBanner>
  {/if}
  <p class="hint">Deleting a movie permanently removes it along with its credits, genres, and artwork.</p>
  <button type="button" class="danger" on:click={() => (confirmDeleteOpen = true)}>Delete movie</button>
</section>

<CreditDialog bind:open={creditOpen} roles={data.roles} on:close={() => (creditOpen = false)} />

<ConfirmDialog
  bind:open={confirmDeleteOpen}
  title="Delete this movie?"
  confirmLabel="Delete movie"
  danger
  on:confirm={submitDelete}
>
  This permanently removes “{movie.title}” and its credits, genres, and artwork. This cannot be undone.
</ConfirmDialog>

<ConfirmDialog
  bind:open={confirmRemoveOpen}
  title="Remove this credit?"
  confirmLabel="Remove from movie"
  danger
  on:confirm={submitRemoveCredit}
>
  This removes {pendingRemoveCredit?.person.name ?? 'this person'} from “{movie.title}”. The person themselves is not
  deleted, and the credit can be added back later.
</ConfirmDialog>

<form id="delete-movie-form" method="POST" action="?/delete" use:enhance hidden></form>

<form
  bind:this={removeCreditForm}
  method="POST"
  action="?/removeCredit"
  use:enhance={() => async ({ update }) => {
    await update();
    // The removed row's button is gone from the DOM; move focus somewhere
    // stable and meaningful instead of letting it silently fall to <body>.
    addCreditButton?.focus();
  }}
  hidden
>
  <input type="hidden" name="creditId" value={pendingRemoveCredit?.id ?? ''} />
</form>

<style>
  .back { display: inline-block; margin-bottom: var(--sp-2); color: var(--text-muted); }
  .section { border: 1px solid var(--border); border-radius: var(--radius); padding: var(--sp-2); margin-bottom: var(--sp-2); }
  .section h2 { margin-top: 0; }
  .grid-2 { display: grid; grid-template-columns: 1fr; gap: var(--sp-2); }
  @media (min-width: 560px) { .grid-2 { grid-template-columns: 1fr 1fr; } }
  .current-art { width: 160px; aspect-ratio: 2/3; object-fit: cover; border-radius: var(--radius); display: block; margin-bottom: var(--sp-1); }
  .credits-head { display: flex; justify-content: space-between; align-items: center; }
  .hint { color: var(--text-muted); }
  .credit-list { list-style: none; padding: 0; margin-top: var(--sp-2); }
  .credit-list li { display: flex; justify-content: space-between; align-items: center; gap: var(--sp-2); padding: var(--sp-1) 0; border-bottom: 1px solid var(--border); }
  .cat-badge { font-size: 0.7rem; font-weight: 700; letter-spacing: 0.05em; color: var(--text-muted); border: 1px solid var(--border); border-radius: 4px; padding: 1px 6px; margin-right: 6px; }
  .unavailable { color: var(--text-muted); font-style: italic; }
  .danger-zone { border-color: var(--danger); }
  .danger-zone h2 { color: var(--danger); }
</style>
