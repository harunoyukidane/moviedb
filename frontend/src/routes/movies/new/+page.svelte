<script lang="ts">
  import type { PageData, ActionData } from './$types';
  import { enhance } from '$app/forms';
  import StateBanner from '$lib/components/StateBanner.svelte';
  import GenreMultiSelect from '$lib/components/GenreMultiSelect.svelte';
  import LanguageSelect from '$lib/components/LanguageSelect.svelte';
  import DateField from '$lib/components/DateField.svelte';

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

<a class="page-back" href="/movies">← All movies</a>
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
  <section class="form-section" aria-label="Movie details">
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
    <div class="form-grid-2">
      <div class="field">
        <label for="releaseDate">Release date</label>
        <DateField id="releaseDate" name="releaseDate" value={v.releaseDate} />
      </div>
      <div class="field">
        <label for="runtimeMinutes">Runtime (min)</label>
        <input id="runtimeMinutes" name="runtimeMinutes" type="number" min="1" value={v.runtimeMinutes ?? ''} />
      </div>
    </div>
    <div class="field">
      <LanguageSelect languages={data.languages} selected={v.originalLanguage ?? null} />
    </div>
    <GenreMultiSelect genres={data.genres} selected={v.genreCodes ?? []} />
  </section>

  <section class="form-section" aria-label="Artwork">
    <h2>Artwork</h2>
    <p class="hint">You can upload a poster after creating the movie, from its editor.</p>
  </section>

  <button type="submit" class="primary" disabled={submitting}>
    {submitting ? 'Creating…' : 'Create movie'}
  </button>
</form>
