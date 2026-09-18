<script lang="ts">
  import type { PageData, ActionData } from './$types';
  import { enhance } from '$app/forms';
  import StateBanner from '$lib/components/StateBanner.svelte';
  import FieldError from '$lib/components/FieldError.svelte';
  import CharCounter from '$lib/components/CharCounter.svelte';
  import ArtworkUpload from '$lib/components/ArtworkUpload.svelte';
  import DateField from '$lib/components/DateField.svelte';
  import CountrySelect from '$lib/components/CountrySelect.svelte';
  import StalenessBanner from '$lib/components/StalenessBanner.svelte';
  import { BIRTH_DATE_MIN, birthDateMax, DEATH_DATE_MIN, deathDateMax } from '$lib/dateBounds';
  import ConfirmDialog from '$lib/components/ConfirmDialog.svelte';
  import { onMount, tick } from 'svelte';

  export let data: PageData;
  export let form: ActionData;

  $: person = data.person;
  let saving = false;
  let photoError = false;
  let confirmDeleteOpen = false;
  let detailsForm: HTMLFormElement;
  let stale = false;

  async function checkStale() {
    try {
      const res = await fetch(`/api/people/${person.id}/version`);
      if (!res.ok) return;
      const data = (await res.json()) as { version: number | null };
      if (data.version !== null && data.version !== person.version) stale = true;
    } catch {
      // A failed check is silently skipped - never blocks or warns falsely.
    }
  }

  onMount(() => {
    window.addEventListener('focus', checkStale);
    return () => window.removeEventListener('focus', checkStale);
  });
  $: fieldErrors = (
    form && 'fieldErrors' in form && form.section === 'details' ? (form.fieldErrors ?? {}) : {}
  ) as Record<string, string>;

  const NAME_MAX = 300;
  const BIOGRAPHY_MAX = 5000;
  const PLACE_OF_BIRTH_MAX = 300;

  // On a failed update, re-render what the user typed (F16/F22) rather than
  // silently falling back to the last-saved value.
  interface DetailsValues {
    name?: string;
    biography?: string;
    birthDate?: string;
    deathDate?: string;
    placeOfBirth?: string;
    birthCountryCode?: string;
  }
  $: submittedValues = (
    form && 'values' in form && form.section === 'details' ? form.values : undefined
  ) as DetailsValues | undefined;
  $: v = {
    name: submittedValues?.name ?? person.name,
    biography: submittedValues?.biography ?? person.biography,
    birthDate: submittedValues?.birthDate ?? person.birthDate ?? '',
    deathDate: submittedValues?.deathDate ?? person.deathDate ?? '',
    placeOfBirth: submittedValues?.placeOfBirth ?? person.placeOfBirth ?? '',
    birthCountryCode: submittedValues?.birthCountryCode ?? person.birthCountryCode ?? ''
  };

  $: nameLength = v.name.length;
  $: biographyLength = v.biography.length;
  $: placeOfBirthLength = v.placeOfBirth.length;

  async function focusFirstInvalid() {
    await tick();
    const field = Object.keys(fieldErrors)[0];
    if (!field) return;
    const el = detailsForm?.querySelector<HTMLElement>(`#${field}`);
    el?.focus();
  }

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
  <StalenessBanner bind:visible={stale} />
  <form
    method="POST"
    action="?/update"
    bind:this={detailsForm}
    use:enhance={async () => {
      await checkStale();
      saving = true;
      return async ({ update }) => {
        await update();
        saving = false;
        await focusFirstInvalid();
      };
    }}
  >
    <input type="hidden" name="expectedVersion" value={person.version} />
    <!-- Last-known-good values (V2.2-11): the action diffs submitted fields
         against these to send a narrow mask, so two edits touching different
         fields don't collide (F21). Always the loaded record, never `v` -
         a prior failed submit's re-rendered values must not become the base. -->
    <input type="hidden" name="base.name" value={person.name} />
    <input type="hidden" name="base.biography" value={person.biography} />
    <input type="hidden" name="base.birthDate" value={person.birthDate ?? ''} />
    <input type="hidden" name="base.deathDate" value={person.deathDate ?? ''} />
    <input type="hidden" name="base.placeOfBirth" value={person.placeOfBirth ?? ''} />
    <input type="hidden" name="base.birthCountryCode" value={person.birthCountryCode ?? ''} />
    <div class="field">
      <label for="name">Name *</label>
      <input
        id="name"
        name="name"
        required
        maxlength={NAME_MAX}
        value={v.name}
        on:input={(e) => (nameLength = e.currentTarget.value.length)}
        aria-invalid={!!fieldErrors.name}
        aria-describedby="name-error"
      />
      <CharCounter count={nameLength} max={NAME_MAX} />
      <FieldError id="name-error" message={fieldErrors.name} />
    </div>
    <div class="field">
      <label for="biography">Biography</label>
      <textarea
        id="biography"
        name="biography"
        rows="4"
        maxlength={BIOGRAPHY_MAX}
        on:input={(e) => (biographyLength = e.currentTarget.value.length)}
        aria-invalid={!!fieldErrors.biography}
        aria-describedby="biography-error">{v.biography}</textarea
      >
      <CharCounter count={biographyLength} max={BIOGRAPHY_MAX} />
      <FieldError id="biography-error" message={fieldErrors.biography} />
    </div>
    <div class="form-grid-2">
      <div class="field">
        <label for="birthDate">Birth date</label>
        <DateField
          id="birthDate"
          name="birthDate"
          value={v.birthDate}
          min={BIRTH_DATE_MIN}
          max={birthDateMax()}
        />
        <FieldError id="birthDate-error" message={fieldErrors.birthDate} />
      </div>
      <div class="field">
        <label for="deathDate">Death date</label>
        <DateField
          id="deathDate"
          name="deathDate"
          value={v.deathDate}
          min={DEATH_DATE_MIN}
          max={deathDateMax()}
        />
        <FieldError id="deathDate-error" message={fieldErrors.deathDate} />
      </div>
    </div>
    <div class="field">
      <label for="placeOfBirth">Place of birth</label>
      <input
        id="placeOfBirth"
        name="placeOfBirth"
        maxlength={PLACE_OF_BIRTH_MAX}
        value={v.placeOfBirth}
        on:input={(e) => (placeOfBirthLength = e.currentTarget.value.length)}
        aria-invalid={!!fieldErrors.placeOfBirth}
        aria-describedby="placeOfBirth-error"
      />
      <CharCounter count={placeOfBirthLength} max={PLACE_OF_BIRTH_MAX} />
      <FieldError id="placeOfBirth-error" message={fieldErrors.placeOfBirth} />
    </div>
    <div class="field">
      <CountrySelect countries={data.countries} selected={v.birthCountryCode || null} />
      <FieldError id="birthCountryCode-error" message={fieldErrors.birthCountryCode} />
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
