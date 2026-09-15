<script lang="ts">
  import Icon from '$lib/components/Icon.svelte';

  export let view: 'cluster' | 'list' = 'cluster';
  /** Hrefs are computed by the caller so the current filter/offset params are preserved. */
  export let clusterHref: string;
  export let listHref: string;
</script>

<!-- Plain links (like the pager and "Clear filters" elsewhere on this page), so
     the view preference works without JS and is fully SSR-deterministic from
     the `view` query param. -->
<div class="view-toggle" role="group" aria-label="Movie view">
  <a
    href={clusterHref}
    class="toggle-btn"
    class:active={view === 'cluster'}
    aria-current={view === 'cluster' ? 'true' : undefined}
    title="Cluster view"
  >
    <Icon name="view-cluster" />
    <span class="visually-hidden">Cluster view</span>
  </a>
  <a
    href={listHref}
    class="toggle-btn"
    class:active={view === 'list'}
    aria-current={view === 'list' ? 'true' : undefined}
    title="List view"
  >
    <Icon name="view-list" />
    <span class="visually-hidden">List view</span>
  </a>
</div>

<style>
  .view-toggle {
    display: inline-flex;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    overflow: hidden;
    flex: 0 0 auto;
  }
  .toggle-btn {
    display: grid;
    place-items: center;
    /* >= 44px target size (WCAG 2.5.5) around the 20px icon. */
    width: 2.75rem;
    height: 2.75rem;
    background: var(--surface);
    color: var(--text-muted);
    border-right: 1px solid var(--border);
    text-decoration: none;
  }
  .toggle-btn:last-child {
    border-right: none;
  }
  .toggle-btn.active {
    background: var(--accent);
    color: var(--accent-contrast);
  }
  .visually-hidden {
    position: absolute;
    width: 1px;
    height: 1px;
    overflow: hidden;
    clip: rect(0 0 0 0);
  }
</style>
