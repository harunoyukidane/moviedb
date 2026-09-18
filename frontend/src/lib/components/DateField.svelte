<script lang="ts">
  import IconButton from './IconButton.svelte';

  export let id: string;
  export let name: string;
  export let value: string | null | undefined = '';
  export let min: string | undefined = undefined;
  export let max: string | undefined = undefined;

  let inputEl: HTMLInputElement | null = null;

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
  <input {id} {name} type="date" value={value ?? ''} {min} {max} bind:this={inputEl} />
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
