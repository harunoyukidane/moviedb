<script lang="ts">
  import type { PageData, ActionData } from './$types';
  import { enhance } from '$app/forms';
  import StateBanner from '$lib/components/StateBanner.svelte';
  import GenreMultiSelect from '$lib/components/GenreMultiSelect.svelte';
  import ArtworkUpload from '$lib/components/ArtworkUpload.svelte';
  import CreditDialog from '$lib/components/CreditDialog.svelte';

  export let data: PageData;
  export let form: ActionData;

  $: movie = data.movie;
  let savingDetails = false;
  let creditOpen = false;

  $: selectedGenres = movie.genres.map((g) => g.code);
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
    <button type="button" class="primary" on:click={() => (creditOpen = true)}>Add credit</button>
  </div>
  {#if form?.creditAdded}
    <StateBanner variant="info">Credit added.</StateBanner>
  {/if}
  {#if form?.message && form?.section === 'credit'}
    <StateBanner variant="error">{form.message}</StateBanner>
  {/if}
  <p class="hint">Cast: {movie.cast.length} · Creators: {movie.creators.length}. Manage individual credits from the movie page.</p>
</section>

<CreditDialog bind:open={creditOpen} roles={data.roles} on:close={() => (creditOpen = false)} />

<style>
  .back { display: inline-block; margin-bottom: var(--sp-2); color: var(--text-muted); }
  .section { border: 1px solid var(--border); border-radius: var(--radius); padding: var(--sp-2); margin-bottom: var(--sp-2); }
  .section h2 { margin-top: 0; }
  .grid-2 { display: grid; grid-template-columns: 1fr; gap: var(--sp-2); }
  @media (min-width: 560px) { .grid-2 { grid-template-columns: 1fr 1fr; } }
  .current-art { width: 160px; aspect-ratio: 2/3; object-fit: cover; border-radius: var(--radius); display: block; margin-bottom: var(--sp-1); }
  .credits-head { display: flex; justify-content: space-between; align-items: center; }
  .hint { color: var(--text-muted); }
</style>
