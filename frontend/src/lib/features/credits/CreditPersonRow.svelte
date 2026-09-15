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
    class="credit-link"
    class:credit-link-disabled={!available}
    href={available ? `/people/${personId}` : undefined}
  >
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
    <span class="credit-name" class:unavailable={!available} title={available ? personName : undefined}>
      {available ? personName : 'Unknown person'}
    </span>
    {#if roleText}<span class="credit-role">{roleText}</span>{/if}
  </svelte:element>
</li>

<style>
  .credit-card {
    display: flex;
  }
  .credit-link {
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
  .credit-link-disabled {
    cursor: default;
  }
  .credit-photo {
    width: 100%;
  }
  .credit-photo img,
  .credit-photo :global(.person-photo-fallback) {
    width: 100%;
    aspect-ratio: 1 / 1;
    border-radius: 50%;
    object-fit: cover;
  }
  .credit-name {
    font-weight: 600;
    margin-top: 4px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    max-width: 100%;
  }
  .credit-name.unavailable {
    color: var(--text-muted);
    font-style: italic;
  }
  .credit-role {
    color: var(--text-muted);
    font-size: 0.875rem;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    max-width: 100%;
  }
</style>
