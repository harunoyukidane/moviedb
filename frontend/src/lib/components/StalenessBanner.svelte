<script lang="ts">
  // Non-blocking early warning (V2.2-11): re-checks the record's version on
  // window focus and just before submit, and shows a dismissible banner if it
  // moved. Delivers the "warn me early" half of what an edit lock promises,
  // without a lock's shared state, heartbeat, or TTL - `@Version` still does
  // the actual enforcement server-side, unaffected by this banner either way.
  export let visible = false;
</script>

{#if visible}
  <div class="staleness-banner" role="status">
    <span>This was changed by someone else since you opened it. Reload to get the latest.</span>
    <button type="button" on:click={() => (visible = false)}>Dismiss</button>
  </div>
{/if}

<style>
  .staleness-banner {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: var(--sp-2);
    background: rgba(224, 182, 74, 0.12);
    border: 1px solid var(--accent);
    border-radius: var(--radius);
    padding: var(--sp-1) var(--sp-2);
    margin-bottom: var(--sp-2);
  }
  .staleness-banner button {
    flex: 0 0 auto;
    background: transparent;
    border: 1px solid var(--border);
  }
</style>
