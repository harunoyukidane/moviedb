<script lang="ts">
  import type { PageData, ActionData } from './$types';
  import { enhance } from '$app/forms';
  import StateBanner from '$lib/components/StateBanner.svelte';
  import ArtworkUpload from '$lib/components/ArtworkUpload.svelte';
  import DateField from '$lib/components/DateField.svelte';
  import ConfirmDialog from '$lib/components/ConfirmDialog.svelte';

  export let data: PageData;
  export let form: ActionData;

  $: person = data.person;
  let saving = false;
  let photoError = false;
  let confirmDeleteOpen = false;

  function submitDelete() {
    const f = document.getElementById('delete-person-form');
    if (f instanceof HTMLFormElement) f.requestSubmit();
  }
</script>

<a class="page-back" href={`/people/${person.id}`}>← Back to person</a>
<h1>Edit “{person.name}”</h1>

{#if form?.updated}
  <StateBanner variant="info">Changes saved.</StateBanner>
{/if}

<section class="form-section" aria-label="Person details">
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
    <div class="form-grid-2">
      <div class="field">
        <label for="birthDate">Birth date</label>
        <DateField id="birthDate" name="birthDate" value={person.birthDate} />
      </div>
      <div class="field">
        <label for="deathDate">Death date</label>
        <DateField id="deathDate" name="deathDate" value={person.deathDate} />
      </div>
    </div>
    <div class="field">
      <label for="placeOfBirth">Place of birth</label>
      <input id="placeOfBirth" name="placeOfBirth" value={person.placeOfBirth ?? ''} />
    </div>
    <button type="submit" class="primary" disabled={saving}>{saving ? 'Saving…' : 'Save changes'}</button>
  </form>
</section>

<section class="form-section" id="photo" aria-label="Photo">
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

<section class="form-section danger-zone" aria-label="Danger zone">
  <h2>Danger zone</h2>
  {#if form?.message && form?.section === 'danger'}
    <StateBanner variant="error">{form.message}</StateBanner>
  {/if}
  <p class="hint">
    Deleting a person permanently removes them. If they are still credited on any movie, the delete will be
    blocked until you remove those credits.
  </p>
  <button type="button" class="danger" on:click={() => (confirmDeleteOpen = true)}>Delete person</button>
</section>

<ConfirmDialog
  bind:open={confirmDeleteOpen}
  title="Delete this person?"
  confirmLabel="Delete person"
  danger
  on:confirm={submitDelete}
>
  This removes “{person.name}” entirely. If they are still credited on any movie, the delete will be blocked
  until you remove those credits.
</ConfirmDialog>

<form id="delete-person-form" method="POST" action="?/delete" use:enhance hidden></form>

<style>
  .current { width: 140px; aspect-ratio: 1; object-fit: cover; border-radius: var(--radius); display: block; margin-bottom: var(--sp-1); }
</style>
