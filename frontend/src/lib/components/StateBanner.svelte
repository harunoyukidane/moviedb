<script lang="ts">
  import { tick } from 'svelte';

  export let variant: 'error' | 'info' = 'info';
  /** Moves focus here (and scrolls it into view) as soon as this banner
      mounts - use for confirmations easy to miss, like a save banner above a
      long form. Pair with a `{#key ...}` at the call site so a repeat
      confirmation (e.g. saving twice in a row) remounts and re-focuses
      instead of silently no-op'ing because the DOM node didn't change. */
  export let autofocus = false;

  function focusOnMount(node: HTMLElement) {
    if (autofocus) tick().then(() => node.focus());
  }
</script>

<div
  class="banner {variant === 'error' ? 'banner-error' : 'banner-info'}"
  role={variant === 'error' ? 'alert' : 'status'}
  tabindex="-1"
  use:focusOnMount
>
  <slot />
</div>
