<script lang="ts">
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
    <svg viewBox="0 -960 960 960" width="20" height="20" fill="currentColor" aria-hidden="true">
      <path
        d="M340-540H200q-33 0-56.5-23.5T120-620v-140q0-33 23.5-56.5T200-840h140q33 0 56.5 23.5T420-760v140q0 33-23.5 56.5T340-540Zm-140-80h140v-140H200v140Zm140 500H200q-33 0-56.5-23.5T120-200v-140q0-33 23.5-56.5T200-420h140q33 0 56.5 23.5T420-340v140q0 33-23.5 56.5T340-120Zm-140-80h140v-140H200v140Zm560-340H620q-33 0-56.5-23.5T540-620v-140q0-33 23.5-56.5T620-840h140q33 0 56.5 23.5T840-760v140q0 33-23.5 56.5T760-540Zm-140-80h140v-140H620v140Zm140 500H620q-33 0-56.5-23.5T540-200v-140q0-33 23.5-56.5T620-420h140q33 0 56.5 23.5T840-340v140q0 33-23.5 56.5T760-120Zm-140-80h140v-140H620v140ZM340-620Zm0 280Zm280-280Zm0 280Z"
      />
    </svg>
    <span class="visually-hidden">Cluster view</span>
  </a>
  <a
    href={listHref}
    class="toggle-btn"
    class:active={view === 'list'}
    aria-current={view === 'list' ? 'true' : undefined}
    title="List view"
  >
    <svg viewBox="0 -960 960 960" width="20" height="20" fill="currentColor" aria-hidden="true">
      <path
        d="M360-240h440v-107H360v107ZM160-613h120v-107H160v107Zm0 187h120v-107H160v107Zm0 186h120v-107H160v107Zm200-186h440v-107H360v107Zm0-187h440v-107H360v107ZM160-160q-33 0-56.5-23.5T80-240v-480q0-33 23.5-56.5T160-800h640q33 0 56.5 23.5T880-720v480q0 33-23.5 56.5T800-160H160Z"
      />
    </svg>
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
    padding: var(--sp-1);
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
