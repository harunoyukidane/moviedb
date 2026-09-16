<script lang="ts">
  import Icon from './Icon.svelte';

  export let icon: 'delete' | 'view-cluster' | 'view-list' | 'calendar';
  /** Required: becomes both the accessible name (aria-label) and the tooltip. */
  export let label: string;
  export let variant: 'default' | 'danger' = 'default';
  export let type: 'button' | 'submit' = 'button';
</script>

<!-- A real <button> with an explicit aria-label — never an icon with no
     accessible name. `title` gives sighted mouse users the same text as a
     tooltip. Target size is >= 44px (WCAG 2.5.5) for comfortable touch/click. -->
<button
  {type}
  class="icon-btn"
  class:danger={variant === 'danger'}
  aria-label={label}
  title={label}
  on:click
>
  <Icon name={icon} />
</button>
