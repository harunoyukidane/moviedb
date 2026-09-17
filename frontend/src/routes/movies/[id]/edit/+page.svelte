<script lang="ts">
  import type { PageData, ActionData } from './$types';
  import { enhance } from '$app/forms';
  import StateBanner from '$lib/components/StateBanner.svelte';
  import FieldError from '$lib/components/FieldError.svelte';
  import GenreMultiSelect from '$lib/components/GenreMultiSelect.svelte';
  import LanguageSelect from '$lib/components/LanguageSelect.svelte';
  import DateField from '$lib/components/DateField.svelte';
  import ArtworkUpload from '$lib/components/ArtworkUpload.svelte';
  import CreditDialog from '$lib/components/CreditDialog.svelte';
  import ConfirmDialog from '$lib/components/ConfirmDialog.svelte';
  import IconButton from '$lib/components/IconButton.svelte';
  import type { MovieCredit } from '$lib/server/types';
  import { tick } from 'svelte';

  export let data: PageData;
  export let form: ActionData;

  $: movie = data.movie;
  let savingDetails = false;
  let creditOpen = false;
  let confirmDeleteOpen = false;
  let addCreditButton: HTMLButtonElement;
  let detailsForm: HTMLFormElement;
  $: fieldErrors = (form?.section === 'details' ? (form?.fieldErrors ?? {}) : {}) as Record<string, string>;

  async function focusFirstInvalid() {
    await tick();
    const field = Object.keys(fieldErrors)[0];
    if (!field) return;
    const el = detailsForm?.querySelector<HTMLElement>(`#${field}`);
    el?.focus();
  }

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
</script>

<a class="page-back" href={`/movies/${movie.id}`}>← Back to movie</a>
<h1>Edit “{movie.title}”</h1>

{#if form?.updated}
  <StateBanner variant="info">Changes saved.</StateBanner>
{/if}

<section class="form-section" aria-label="Movie details">
  <h2>Details</h2>
  {#if form?.message && form?.section === 'details'}
    <StateBanner variant="error">{form.message}</StateBanner>
  {/if}
  <form
    method="POST"
    action="?/update"
    bind:this={detailsForm}
    use:enhance={() => {
      savingDetails = true;
      return async ({ update }) => {
        // Fields here are populated from server data (`value={movie.title}`, etc.),
        // not via bind:value, so the default reset-to-defaultValue on success would
        // blank them until the user reloads: reload data without resetting the form.
        await update({ reset: false });
        savingDetails = false;
        await focusFirstInvalid();
      };
    }}
  >
    <input type="hidden" name="expectedVersion" value={movie.version} />
    <div class="field">
      <label for="title">Title *</label>
      <input
        id="title"
        name="title"
        required
        value={movie.title}
        aria-invalid={!!fieldErrors.title}
        aria-describedby="title-error"
      />
      <FieldError id="title-error" message={fieldErrors.title} />
    </div>
    <div class="field">
      <label for="originalTitle">Original title</label>
      <input
        id="originalTitle"
        name="originalTitle"
        value={movie.originalTitle ?? ''}
        aria-invalid={!!fieldErrors.originalTitle}
        aria-describedby="originalTitle-error"
      />
      <FieldError id="originalTitle-error" message={fieldErrors.originalTitle} />
    </div>
    <div class="field">
      <label for="synopsis">Synopsis</label>
      <textarea
        id="synopsis"
        name="synopsis"
        rows="4"
        aria-invalid={!!fieldErrors.synopsis}
        aria-describedby="synopsis-error">{movie.synopsis}</textarea
      >
      <FieldError id="synopsis-error" message={fieldErrors.synopsis} />
    </div>
    <div class="form-grid-2">
      <div class="field">
        <label for="releaseDate">Release date</label>
        <DateField id="releaseDate" name="releaseDate" value={movie.releaseDate} />
        <FieldError id="releaseDate-error" message={fieldErrors.releaseDate} />
      </div>
      <div class="field">
        <label for="runtimeMinutes">Runtime (min)</label>
        <input
          id="runtimeMinutes"
          name="runtimeMinutes"
          type="number"
          min="1"
          value={movie.runtimeMinutes ?? ''}
          aria-invalid={!!fieldErrors.runtimeMinutes}
          aria-describedby="runtimeMinutes-error"
        />
        <FieldError id="runtimeMinutes-error" message={fieldErrors.runtimeMinutes} />
      </div>
    </div>
    <div class="field">
      <LanguageSelect languages={data.languages} selected={movie.originalLanguage ?? null} />
      <FieldError id="originalLanguage-error" message={fieldErrors.originalLanguage} />
    </div>
    <GenreMultiSelect genres={data.genres} selected={selectedGenres} />
    <FieldError id="genreCodes-error" message={fieldErrors.genreCodes} />
    <button type="submit" class="primary" disabled={savingDetails}>
      {savingDetails ? 'Saving…' : 'Save changes'}
    </button>
  </form>
</section>

<section class="form-section" id="artwork" aria-label="Artwork">
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

<section class="form-section" aria-label="Credits">
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
          <form
            method="POST"
            action="?/removeCredit"
            use:enhance={() => async ({ update }) => {
              await update();
              // The removed row's button is gone from the DOM; move focus somewhere
              // stable and meaningful instead of letting it silently fall to <body>.
              addCreditButton?.focus();
            }}
          >
            <input type="hidden" name="creditId" value={c.id} />
            <IconButton type="submit" icon="delete" variant="danger" label={`Remove ${c.person.name} from movie`} />
          </form>
        </li>
      {/each}
    </ul>
  {/if}
</section>

<section class="form-section danger-zone" aria-label="Danger zone">
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

<form id="delete-movie-form" method="POST" action="?/delete" use:enhance hidden></form>

<style>
  .current-art { width: 160px; aspect-ratio: 2/3; object-fit: cover; border-radius: var(--radius); display: block; margin-bottom: var(--sp-1); }
  .credits-head { display: flex; justify-content: space-between; align-items: center; }
  .credit-list { list-style: none; padding: 0; margin-top: var(--sp-2); }
  .credit-list li { display: flex; justify-content: space-between; align-items: center; gap: var(--sp-2); padding: var(--sp-1) 0; border-bottom: 1px solid var(--border); }
  .cat-badge { font-size: 0.7rem; font-weight: 700; letter-spacing: 0.05em; color: var(--text-muted); border: 1px solid var(--border); border-radius: 4px; padding: 1px 6px; margin-right: 6px; }
  .unavailable { color: var(--text-muted); font-style: italic; }
</style>
