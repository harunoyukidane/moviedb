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

<style>
  .icon-btn {
    display: inline-grid;
    place-items: center;
    width: 2.75rem;
    height: 2.75rem;
    border-radius: var(--radius);
    border: 1px solid var(--border);
    background: var(--surface);
    color: var(--text);
  }
  .icon-btn:hover {
    background: var(--surface-2);
  }
  .icon-btn:focus-visible {
    outline: 2px solid var(--accent);
    outline-offset: 2px;
  }
  .icon-btn.danger {
    color: var(--danger);
    border-color: var(--danger);
  }
</style>
