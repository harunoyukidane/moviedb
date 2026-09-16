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
