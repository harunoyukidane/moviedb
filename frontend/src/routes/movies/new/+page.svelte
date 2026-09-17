<script lang="ts">
  import type { PageData, ActionData } from './$types';
  import { enhance } from '$app/forms';
  import StateBanner from '$lib/components/StateBanner.svelte';
  import FieldError from '$lib/components/FieldError.svelte';
  import GenreMultiSelect from '$lib/components/GenreMultiSelect.svelte';
  import LanguageSelect from '$lib/components/LanguageSelect.svelte';
  import DateField from '$lib/components/DateField.svelte';
  import { tick } from 'svelte';

  export let data: PageData;
  export let form: ActionData;

  let submitting = false;
  let formEl: HTMLFormElement;
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
  $: fieldErrors = (form?.fieldErrors ?? {}) as Record<string, string>;

  async function focusFirstInvalid() {
    await tick();
    const field = Object.keys(fieldErrors)[0];
    if (!field) return;
    const el = formEl?.querySelector<HTMLElement>(`#${field}`);
    el?.focus();
  }
</script>

<a class="page-back" href="/movies">← All movies</a>
<h1>New movie</h1>

{#if form?.message}
  <StateBanner variant="error">{form.message}</StateBanner>
{/if}

<form
  method="POST"
  bind:this={formEl}
  use:enhance={() => {
    submitting = true;
    return async ({ update }) => {
      await update();
      submitting = false;
      await focusFirstInvalid();
    };
  }}
>
  <section class="form-section" aria-label="Movie details">
    <h2>Details</h2>
    <div class="field">
      <label for="title">Title *</label>
      <input
        id="title"
        name="title"
        required
        value={v.title ?? ''}
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
        value={v.originalTitle ?? ''}
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
        aria-describedby="synopsis-error">{v.synopsis ?? ''}</textarea
      >
      <FieldError id="synopsis-error" message={fieldErrors.synopsis} />
    </div>
    <div class="form-grid-2">
      <div class="field">
        <label for="releaseDate">Release date</label>
        <DateField id="releaseDate" name="releaseDate" value={v.releaseDate} />
        <FieldError id="releaseDate-error" message={fieldErrors.releaseDate} />
      </div>
      <div class="field">
        <label for="runtimeMinutes">Runtime (min)</label>
        <input
          id="runtimeMinutes"
          name="runtimeMinutes"
          type="number"
          min="1"
          value={v.runtimeMinutes ?? ''}
          aria-invalid={!!fieldErrors.runtimeMinutes}
          aria-describedby="runtimeMinutes-error"
        />
        <FieldError id="runtimeMinutes-error" message={fieldErrors.runtimeMinutes} />
      </div>
    </div>
    <div class="field">
      <LanguageSelect languages={data.languages} selected={v.originalLanguage ?? null} />
      <FieldError id="originalLanguage-error" message={fieldErrors.originalLanguage} />
    </div>
    <GenreMultiSelect genres={data.genres} selected={v.genreCodes ?? []} />
    <FieldError id="genreCodes-error" message={fieldErrors.genreCodes} />
  </section>

  <section class="form-section" aria-label="Artwork">
    <h2>Artwork</h2>
    <p class="hint">You can upload a poster after creating the movie, from its editor.</p>
  </section>

  <button type="submit" class="primary" disabled={submitting}>
    {submitting ? 'Creating…' : 'Create movie'}
  </button>
</form>
