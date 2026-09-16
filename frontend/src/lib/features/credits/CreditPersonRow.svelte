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

<li class="credit-card">
  <!-- Only an available person has a valid detail page to route to; an
       unavailable People reference stays inert, matching the read-only
       "Unknown person" placeholder shown elsewhere for that case. -->
  <svelte:element
    this={available ? 'a' : 'span'}
    class="entity-card"
    class:credit-link-disabled={!available}
    href={available ? `/people/${personId}` : undefined}
  >
    <span class="entity-card-photo">
      {#if available && !photoFailed}
        <img
          class="photo-thumb"
          src={`/api/people/${personId}/photo`}
          alt={`Photo of ${personName}`}
          loading="lazy"
          on:error={() => (photoFailed = true)}
        />
      {:else}
        <PersonPhotoFallback />
      {/if}
    </span>
    <span
      class="entity-card-name text-truncate"
      class:unavailable={!available}
      title={available ? personName : undefined}
    >
      {available ? personName : 'Unknown person'}
    </span>
    {#if roleText}<span class="credit-role text-truncate">{roleText}</span>{/if}
  </svelte:element>
</li>

<style>
  .credit-card {
    display: flex;
  }
  .credit-link-disabled {
    cursor: default;
  }
  .entity-card-name.unavailable {
    color: var(--text-muted);
    font-style: italic;
  }
  .credit-role {
    color: var(--text-muted);
    font-size: 0.875rem;
  }
</style>
