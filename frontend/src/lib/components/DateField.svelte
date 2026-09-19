<script lang="ts">
  import IconButton from './IconButton.svelte';

  export let id: string;
  export let name: string;
  export let value: string | null | undefined = '';
  export let min: string | undefined = undefined;
  export let max: string | undefined = undefined;
  /**
   * Marks the control as failing validation, exactly as the plain inputs do.
   * Without it a date error renders its message below the field but leaves the
   * input unmarked: the `input[aria-invalid='true']` border rule never matches,
   * so nothing is visibly highlighted, and a screen reader on the field
   * announces no error (WCAG 3.3.1). Both matter now that birth/death dates can
   * be rejected for conflicting with a credit (V2.8-03).
   */
  export let invalid = false;

  let inputEl: HTMLInputElement | null = null;

  // Every call site renders its message as `<FieldError id="{id}-error">`, the
  // same convention the plain inputs wire up by hand, so the association is
  // derived here rather than repeated as a prop at each one.
  $: describedBy = `${id}-error`;

  // showPicker() needs a user gesture and isn't implemented everywhere (e.g.
  // older Safari); fall back to focusing the field so the native calendar
  // affordance (or at least the keyboard) is still reachable from the button.
  function openPicker() {
    if (typeof inputEl?.showPicker === 'function') {
      try {
        inputEl.showPicker();
        return;
      } catch {
        // fall through to focus
      }
    }
    inputEl?.focus();
  }
</script>

<div class="date-field">
  <input
    {id}
    {name}
    type="date"
    value={value ?? ''}
    {min}
    {max}
    aria-invalid={invalid}
    aria-describedby={describedBy}
    bind:this={inputEl}
  />
  <IconButton icon="calendar" label="Open calendar" on:click={openPicker} />
</div>

<style>
  .date-field {
    display: flex;
    gap: var(--sp-1);
  }
  .date-field input {
    flex: 1 1 auto;
    min-width: 0;
  }
</style>
