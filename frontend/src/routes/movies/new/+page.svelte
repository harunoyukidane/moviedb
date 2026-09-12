<script lang="ts">
  import type { PageData, ActionData } from './$types';
  import { enhance } from '$app/forms';
  import StateBanner from '$lib/components/StateBanner.svelte';
  import GenreMultiSelect from '$lib/components/GenreMultiSelect.svelte';

  export let data: PageData;
  export let form: ActionData;

  let submitting = false;
  interface FormValues {
    title?: string;
    originalTitle?: string;
    synopsis?: string;
    releaseDate?: string;
    runtimeMinutes?: string;
    originalLanguage?: string;
    genreCodes?: string[];
  }
  $: v = (form?.values ?? {}) as FormValues;
</script>

<a class="back" href="/movies">← All movies</a>
<h1>New movie</h1>

{#if form?.message}
  <StateBanner variant="error">{form.message}</StateBanner>
{/if}

<form
  method="POST"
  use:enhance={() => {
    submitting = true;
    return async ({ update }) => {
      await update();
      submitting = false;
    };
  }}
>
  <section class="section" aria-label="Movie details">
    <h2>Details</h2>
    <div class="field">
      <label for="title">Title *</label>
      <input id="title" name="title" required value={v.title ?? ''} />
    </div>
    <div class="field">
      <label for="originalTitle">Original title</label>
      <input id="originalTitle" name="originalTitle" value={v.originalTitle ?? ''} />
    </div>
    <div class="field">
      <label for="synopsis">Synopsis</label>
      <textarea id="synopsis" name="synopsis" rows="4">{v.synopsis ?? ''}</textarea>
    </div>
    <div class="grid-2">
      <div class="field">
        <label for="releaseDate">Release date</label>
        <input id="releaseDate" name="releaseDate" type="date" value={v.releaseDate ?? ''} />
      </div>
      <div class="field">
        <label for="runtimeMinutes">Runtime (min)</label>
        <input id="runtimeMinutes" name="runtimeMinutes" type="number" min="1" value={v.runtimeMinutes ?? ''} />
      </div>
    </div>
    <div class="field">
      <label for="originalLanguage">Original language</label>
      <input id="originalLanguage" name="originalLanguage" maxlength="10" value={v.originalLanguage ?? ''} />
    </div>
    <GenreMultiSelect genres={data.genres} selected={v.genreCodes ?? []} />
  </section>

  <section class="section" aria-label="Artwork">
    <h2>Artwork</h2>
    <p class="hint">You can upload a poster after creating the movie, from its editor.</p>
  </section>

  <button type="submit" class="primary" disabled={submitting}>
    {submitting ? 'Creating…' : 'Create movie'}
  </button>
</form>

<style>
  .back { display: inline-block; margin-bottom: var(--sp-2); color: var(--text-muted); }
  .section { border: 1px solid var(--border); border-radius: var(--radius); padding: var(--sp-2); margin-bottom: var(--sp-2); }
  .section h2 { margin-top: 0; }
  .grid-2 { display: grid; grid-template-columns: 1fr; gap: var(--sp-2); }
  @media (min-width: 560px) { .grid-2 { grid-template-columns: 1fr 1fr; } }
  .hint { color: var(--text-muted); }
</style>
