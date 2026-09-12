<script lang="ts">
  import type { PageData, ActionData } from './$types';
  import { enhance } from '$app/forms';
  import StateBanner from '$lib/components/StateBanner.svelte';
  import ArtworkUpload from '$lib/components/ArtworkUpload.svelte';

  export let data: PageData;
  export let form: ActionData;

  $: person = data.person;
  let saving = false;
  let photoError = false;
</script>

<a class="back" href={`/people/${person.id}`}>← Back to person</a>
<h1>Edit “{person.name}”</h1>

{#if form?.updated}
  <StateBanner variant="info">Changes saved.</StateBanner>
{/if}

<section class="section" aria-label="Person details">
  <h2>Details</h2>
  {#if form?.message && form?.section === 'details'}
    <StateBanner variant="error">{form.message}</StateBanner>
  {/if}
  <form
    method="POST"
    action="?/update"
    use:enhance={() => {
      saving = true;
      return async ({ update }) => {
        await update();
        saving = false;
      };
    }}
  >
    <input type="hidden" name="expectedVersion" value={person.version} />
    <div class="field">
      <label for="name">Name *</label>
      <input id="name" name="name" required value={person.name} />
    </div>
    <div class="field">
      <label for="biography">Biography</label>
      <textarea id="biography" name="biography" rows="4">{person.biography}</textarea>
    </div>
    <div class="grid-2">
      <div class="field">
        <label for="birthDate">Birth date</label>
        <input id="birthDate" name="birthDate" type="date" value={person.birthDate ?? ''} />
      </div>
      <div class="field">
        <label for="deathDate">Death date</label>
        <input id="deathDate" name="deathDate" type="date" value={person.deathDate ?? ''} />
      </div>
    </div>
    <div class="field">
      <label for="placeOfBirth">Place of birth</label>
      <input id="placeOfBirth" name="placeOfBirth" value={person.placeOfBirth ?? ''} />
    </div>
    <button type="submit" class="primary" disabled={saving}>{saving ? 'Saving…' : 'Save changes'}</button>
  </form>
</section>

<section class="section" id="photo" aria-label="Photo">
  <h2>Photo</h2>
  {#if form?.message && form?.section === 'photo'}
    <StateBanner variant="error">{form.message}</StateBanner>
  {/if}
  {#if !photoError}
    <img class="current" src={data.photoUrl} alt={`Current photo of ${person.name}`} on:error={() => (photoError = true)} />
  {/if}
  <p class="hint">An uploaded photo takes precedence over any imported image.</p>
  <ArtworkUpload action="?/uploadPhoto" label="Upload photo" />
</section>

<style>
  .back { display: inline-block; margin-bottom: var(--sp-2); color: var(--text-muted); }
  .section { border: 1px solid var(--border); border-radius: var(--radius); padding: var(--sp-2); margin-bottom: var(--sp-2); }
  .section h2 { margin-top: 0; }
  .grid-2 { display: grid; grid-template-columns: 1fr; gap: var(--sp-2); }
  @media (min-width: 560px) { .grid-2 { grid-template-columns: 1fr 1fr; } }
  .current { width: 140px; aspect-ratio: 1; object-fit: cover; border-radius: var(--radius); display: block; margin-bottom: var(--sp-1); }
  .hint { color: var(--text-muted); }
</style>
