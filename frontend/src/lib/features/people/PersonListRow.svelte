<script lang="ts">
  import PersonPhotoFallback from './PersonPhotoFallback.svelte';

  export let id: string;
  export let name: string;
  export let photoUrl: string | null | undefined = null;
  /** Precomputed display text, e.g. "1990" or "1990–2020"; omitted when absent. */
  export let dates: string | null = null;
</script>

<a class="entity-card" href={`/people/${id}`}>
  <span class="entity-card-photo">
    {#if photoUrl}
      <img class="photo-thumb" src={photoUrl} alt={`Photo of ${name}`} loading="lazy" />
    {:else}
      <PersonPhotoFallback />
    {/if}
  </span>
  <span class="entity-card-name text-truncate" title={name}>{name}</span>
  {#if dates}<span class="card-dates">{dates}</span>{/if}
</a>

<style>
  .entity-card-photo :global(.person-photo-fallback) {
    font-size: 2rem;
  }
  .card-dates {
    color: var(--text-muted);
    font-size: 0.875rem;
  }
</style>
