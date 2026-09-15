<script lang="ts">
  import PersonPhotoFallback from './PersonPhotoFallback.svelte';

  export let id: string;
  export let name: string;
  export let photoUrl: string | null | undefined = null;
  /** Precomputed display text, e.g. "1990" or "1990–2020"; omitted when absent. */
  export let dates: string | null = null;
</script>

<a class="person-row" href={`/people/${id}`}>
  <span class="row-photo">
    {#if photoUrl}
      <img src={photoUrl} alt={`Photo of ${name}`} loading="lazy" />
    {:else}
      <PersonPhotoFallback />
    {/if}
  </span>
  <span class="row-name">{name}</span>
  {#if dates}<span class="row-dates">{dates}</span>{/if}
</a>

<style>
  .person-row {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
    padding: var(--sp-1) var(--sp-2);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    margin-bottom: var(--sp-1);
    text-decoration: none;
    color: var(--text);
    background: var(--surface);
  }
  .row-photo {
    flex: 0 0 auto;
    width: 2.5rem;
  }
  .row-photo img,
  .row-photo :global(.person-photo-fallback) {
    width: 2.5rem;
    height: 2.5rem;
  }
  .row-photo img {
    border-radius: 50%;
    object-fit: cover;
  }
  .row-photo :global(.person-photo-fallback) {
    font-size: 1rem;
  }
  .row-name {
    font-weight: 600;
    flex: 1 1 auto;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .row-dates {
    color: var(--text-muted);
    flex: 0 0 auto;
  }
</style>
