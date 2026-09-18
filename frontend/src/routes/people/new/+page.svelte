<script lang="ts">
  import type { PageData, ActionData } from './$types';
  import { enhance } from '$app/forms';
  import StateBanner from '$lib/components/StateBanner.svelte';
  import FieldError from '$lib/components/FieldError.svelte';
  import CharCounter from '$lib/components/CharCounter.svelte';
  import DateField from '$lib/components/DateField.svelte';
  import CountrySelect from '$lib/components/CountrySelect.svelte';
  import { BIRTH_DATE_MIN, birthDateMax, DEATH_DATE_MIN, deathDateMax } from '$lib/dateBounds';
  import { tick } from 'svelte';

  export let data: PageData;
  export let form: ActionData;
  let submitting = false;
  let formEl: HTMLFormElement;
  interface FormValues {
    name?: string;
    biography?: string;
    birthDate?: string;
    deathDate?: string;
    placeOfBirth?: string;
    birthCountryCode?: string;
  }
  $: v = (form?.values ?? {}) as FormValues;
  $: fieldErrors = (form?.fieldErrors ?? {}) as Record<string, string>;

  const NAME_MAX = 300;
  const BIOGRAPHY_MAX = 5000;
  const PLACE_OF_BIRTH_MAX = 300;
  $: nameLength = (v.name ?? '').length;
  $: biographyLength = (v.biography ?? '').length;
  $: placeOfBirthLength = (v.placeOfBirth ?? '').length;

  async function focusFirstInvalid() {
    await tick();
    const field = Object.keys(fieldErrors)[0];
    if (!field) return;
    const el = formEl?.querySelector<HTMLElement>(`#${field}`);
    el?.focus();
  }
</script>

<a class="page-back" href="/people">← All people</a>
<h1>New person</h1>

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
  <div class="field">
    <label for="name">Name *</label>
    <input
      id="name"
      name="name"
      required
      maxlength={NAME_MAX}
      value={v.name ?? ''}
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
      aria-describedby="biography-error">{v.biography ?? ''}</textarea
    >
    <CharCounter count={biographyLength} max={BIOGRAPHY_MAX} />
    <FieldError id="biography-error" message={fieldErrors.biography} />
  </div>
  <div class="form-grid-2">
    <div class="field">
      <label for="birthDate">Birth date</label>
      <DateField id="birthDate" name="birthDate" value={v.birthDate} min={BIRTH_DATE_MIN} max={birthDateMax()} />
      <FieldError id="birthDate-error" message={fieldErrors.birthDate} />
    </div>
    <div class="field">
      <label for="deathDate">Death date</label>
      <DateField id="deathDate" name="deathDate" value={v.deathDate} min={DEATH_DATE_MIN} max={deathDateMax()} />
      <FieldError id="deathDate-error" message={fieldErrors.deathDate} />
    </div>
  </div>
  <div class="field">
    <label for="placeOfBirth">Place of birth</label>
    <input
      id="placeOfBirth"
      name="placeOfBirth"
      maxlength={PLACE_OF_BIRTH_MAX}
      value={v.placeOfBirth ?? ''}
      on:input={(e) => (placeOfBirthLength = e.currentTarget.value.length)}
      aria-invalid={!!fieldErrors.placeOfBirth}
      aria-describedby="placeOfBirth-error"
    />
    <CharCounter count={placeOfBirthLength} max={PLACE_OF_BIRTH_MAX} />
    <FieldError id="placeOfBirth-error" message={fieldErrors.placeOfBirth} />
  </div>
  <div class="field">
    <CountrySelect countries={data.countries} selected={v.birthCountryCode ?? null} />
    <FieldError id="birthCountryCode-error" message={fieldErrors.birthCountryCode} />
  </div>
  <button type="submit" class="primary" disabled={submitting}>{submitting ? 'Creating…' : 'Create person'}</button>
</form>
