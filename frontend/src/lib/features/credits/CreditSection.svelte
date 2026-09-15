<script lang="ts">
  import type { MovieCredit } from '$lib/server/types';
  import StateBanner from '$lib/components/StateBanner.svelte';
  import CreditPersonRow from './CreditPersonRow.svelte';

  // Rendered in the given order without re-sorting: the Catalogue GraphQL API
  // already returns cast ordered by billing order (nulls last, id tie-break) and
  // crew ordered by role/billing/id (§8.1) — re-sorting here would risk silently
  // diverging from that deterministic, tested ordering.
  export let credits: MovieCredit[] = [];
  export let emptyMessage: string;

  function roleTextFor(c: MovieCredit): string {
    return c.category === 'CAST' ? (c.characterName ?? '') : c.role.title;
  }
</script>

{#if credits.length === 0}
  <StateBanner variant="info">{emptyMessage}</StateBanner>
{:else}
  <ul class="credit-section" aria-label="Credits">
    {#each credits as c (c.id)}
      <CreditPersonRow
        personId={c.person.id}
        personName={c.person.name}
        available={c.person.available}
        roleText={roleTextFor(c)}
      />
    {/each}
  </ul>
{/if}

<style>
  .credit-section {
    list-style: none;
    margin: 0;
    padding: 0;
  }
</style>
