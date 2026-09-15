<script lang="ts">
  import PersonPhotoFallback from '$lib/features/people/PersonPhotoFallback.svelte';

  export let personId: string;
  export let personName: string;
  export let available: boolean;
  /** Character name for CAST, role title for CREW; the caller decides which. */
  export let roleText: string;

  // The photo is a same-origin proxy URL by id (no extra GraphQL field/round trip);
  // a missing photo (404) or an unavailable People reference both fall back to the
  // shared placeholder rather than showing a broken image.
  let photoFailed = false;
</script>

<li class="credit-row">
  <span class="credit-photo">
    {#if available && !photoFailed}
      <img
        src={`/api/people/${personId}/photo`}
        alt={`Photo of ${personName}`}
        loading="lazy"
        on:error={() => (photoFailed = true)}
      />
    {:else}
      <PersonPhotoFallback />
    {/if}
  </span>
  <span class="credit-body">
    <span class="credit-name" class:unavailable={!available}>
      {available ? personName : 'Unknown person'}
    </span>
    {#if roleText}<span class="credit-role">{roleText}</span>{/if}
  </span>
</li>

<style>
  .credit-row {
    display: flex;
    align-items: center;
    gap: var(--sp-2);
    padding: var(--sp-1) 0;
    border-bottom: 1px solid var(--border);
  }
  .credit-photo {
    flex: 0 0 auto;
    width: 2.5rem;
  }
  .credit-photo img {
    width: 2.5rem;
    height: 2.5rem;
    border-radius: 50%;
    object-fit: cover;
  }
  .credit-body {
    display: flex;
    flex-direction: column;
    min-width: 0;
  }
  .credit-name {
    font-weight: 600;
  }
  .credit-name.unavailable {
    color: var(--text-muted);
    font-style: italic;
  }
  .credit-role {
    color: var(--text-muted);
    font-size: 0.875rem;
  }
</style>
