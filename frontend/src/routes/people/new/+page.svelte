<script lang="ts">
  import type { ActionData } from './$types';
  import { enhance } from '$app/forms';
  import StateBanner from '$lib/components/StateBanner.svelte';

  export let form: ActionData;
  let submitting = false;
  interface FormValues {
    name?: string;
    biography?: string;
    birthDate?: string;
    deathDate?: string;
    placeOfBirth?: string;
  }
  $: v = (form?.values ?? {}) as FormValues;
</script>

<a class="back" href="/people">← All people</a>
<h1>New person</h1>

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
  <div class="field">
    <label for="name">Name *</label>
    <input id="name" name="name" required value={v.name ?? ''} />
  </div>
  <div class="field">
    <label for="biography">Biography</label>
    <textarea id="biography" name="biography" rows="4">{v.biography ?? ''}</textarea>
  </div>
  <div class="grid-2">
    <div class="field">
      <label for="birthDate">Birth date</label>
      <input id="birthDate" name="birthDate" type="date" value={v.birthDate ?? ''} />
    </div>
    <div class="field">
      <label for="deathDate">Death date</label>
      <input id="deathDate" name="deathDate" type="date" value={v.deathDate ?? ''} />
    </div>
  </div>
  <div class="field">
    <label for="placeOfBirth">Place of birth</label>
    <input id="placeOfBirth" name="placeOfBirth" value={v.placeOfBirth ?? ''} />
  </div>
  <button type="submit" class="primary" disabled={submitting}>{submitting ? 'Creating…' : 'Create person'}</button>
</form>

<style>
  .back { display: inline-block; margin-bottom: var(--sp-2); color: var(--text-muted); }
  .grid-2 { display: grid; grid-template-columns: 1fr; gap: var(--sp-2); }
  @media (min-width: 560px) { .grid-2 { grid-template-columns: 1fr 1fr; } }
</style>
