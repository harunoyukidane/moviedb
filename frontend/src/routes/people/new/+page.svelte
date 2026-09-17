<script lang="ts">
  import type { ActionData } from './$types';
  import { enhance } from '$app/forms';
  import StateBanner from '$lib/components/StateBanner.svelte';
  import FieldError from '$lib/components/FieldError.svelte';
  import DateField from '$lib/components/DateField.svelte';
  import { tick } from 'svelte';

  export let form: ActionData;
  let submitting = false;
  let formEl: HTMLFormElement;
  interface FormValues {
    name?: string;
    biography?: string;
    birthDate?: string;
    deathDate?: string;
    placeOfBirth?: string;
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
      value={v.name ?? ''}
      aria-invalid={!!fieldErrors.name}
      aria-describedby="name-error"
    />
    <FieldError id="name-error" message={fieldErrors.name} />
  </div>
  <div class="field">
    <label for="biography">Biography</label>
    <textarea
      id="biography"
      name="biography"
      rows="4"
      aria-invalid={!!fieldErrors.biography}
      aria-describedby="biography-error">{v.biography ?? ''}</textarea
    >
    <FieldError id="biography-error" message={fieldErrors.biography} />
  </div>
  <div class="form-grid-2">
    <div class="field">
      <label for="birthDate">Birth date</label>
      <DateField id="birthDate" name="birthDate" value={v.birthDate} />
      <FieldError id="birthDate-error" message={fieldErrors.birthDate} />
    </div>
    <div class="field">
      <label for="deathDate">Death date</label>
      <DateField id="deathDate" name="deathDate" value={v.deathDate} />
      <FieldError id="deathDate-error" message={fieldErrors.deathDate} />
    </div>
  </div>
  <div class="field">
    <label for="placeOfBirth">Place of birth</label>
    <input
      id="placeOfBirth"
      name="placeOfBirth"
      value={v.placeOfBirth ?? ''}
      aria-invalid={!!fieldErrors.placeOfBirth}
      aria-describedby="placeOfBirth-error"
    />
    <FieldError id="placeOfBirth-error" message={fieldErrors.placeOfBirth} />
  </div>
  <button type="submit" class="primary" disabled={submitting}>{submitting ? 'Creating…' : 'Create person'}</button>
</form>
