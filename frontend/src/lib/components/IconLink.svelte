<script lang="ts">
  import Icon from './Icon.svelte';

  export let icon: 'delete' | 'edit' | 'view-cluster' | 'view-list';
  export let href: string;
  /** Required: becomes both the accessible name and the tooltip. */
  export let label: string;
</script>

<!-- Same accessible pattern as IconButton, but for a real navigation link
     (e.g. "Edit movie" -> the edit screen), never a JS-only click handler. -->
<a {href} class="icon-btn" aria-label={label} title={label}>
  <Icon name={icon} />
</a>

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
    text-decoration: none;
  }
  .icon-btn:hover {
    background: var(--surface-2);
  }
  .icon-btn:focus-visible {
    outline: 2px solid var(--accent);
    outline-offset: 2px;
  }
</style>
