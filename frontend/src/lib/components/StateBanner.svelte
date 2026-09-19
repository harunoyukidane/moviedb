<script lang="ts">
  import { tick } from 'svelte';

  export let variant: 'error' | 'info' = 'info';
  /** Moves focus here (and scrolls it into view) as soon as this banner
      mounts - use for confirmations easy to miss, like a save banner above a
      long form. Pair with a `{#key ...}` at the call site so a repeat
      confirmation (e.g. saving twice in a row) remounts and re-focuses
      instead of silently no-op'ing because the DOM node didn't change. */
  export let autofocus = false;

  // tabindex is set here rather than in the markup so only the banners that
  // actually take focus become focusable - the rest stay plain text and are
  // not click-focusable. It also keeps Svelte's a11y checker satisfied: it
  // cannot evaluate a ternary tabindex statically and warns about a possibly
  // non-negative value on a non-interactive element.
  function focusOnMount(node: HTMLElement) {
    if (!autofocus) return;
    node.tabIndex = -1;
    tick().then(() => node.focus());
  }
</script>

<div
  class="banner {variant === 'error' ? 'banner-error' : 'banner-info'}"
  role={variant === 'error' ? 'alert' : 'status'}
  use:focusOnMount
>
  <slot />
</div>
