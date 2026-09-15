<script lang="ts">
  import PersonPhotoFallback from './PersonPhotoFallback.svelte';

  export let id: string;
  export let name: string;
  export let photoUrl: string | null | undefined = null;
  /** Precomputed display text, e.g. "1990" or "1990–2020"; omitted when absent. */
  export let dates: string | null = null;
</script>

<a class="person-card" href={`/people/${id}`}>
  <span class="card-photo">
    {#if photoUrl}
      <img src={photoUrl} alt={`Photo of ${name}`} loading="lazy" />
    {:else}
      <PersonPhotoFallback />
    {/if}
  </span>
  <span class="card-name" title={name}>{name}</span>
  {#if dates}<span class="card-dates">{dates}</span>{/if}
</a>

<style>
  .person-card {
    display: flex;
    flex-direction: column;
    align-items: center;
    text-align: center;
    text-decoration: none;
    color: var(--text);
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    padding: var(--sp-1);
    width: 100%;
    gap: 2px;
  }
  .card-photo {
    width: 100%;
  }
  .card-photo img,
  .card-photo :global(.person-photo-fallback) {
    width: 100%;
    aspect-ratio: 1 / 1;
    border-radius: 50%;
    object-fit: cover;
  }
  .card-photo :global(.person-photo-fallback) {
    font-size: 2rem;
  }
  .card-name {
    font-weight: 600;
    margin-top: 4px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    max-width: 100%;
  }
  .card-dates {
    color: var(--text-muted);
    font-size: 0.875rem;
  }
</style>
