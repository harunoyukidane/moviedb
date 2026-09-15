<script lang="ts">
  import type { PageData } from './$types';
  import StateBanner from '$lib/components/StateBanner.svelte';
  import PersonListRow from '$lib/features/people/PersonListRow.svelte';

  export let data: PageData;
  $: page = data.page;
  $: hasPrev = page.offset > 0;
  $: hasNext = page.offset + page.limit < page.total;

  function datesFor(person: { birthDate?: string | null; deathDate?: string | null }): string | null {
    if (!person.birthDate) return null;
    const born = person.birthDate.slice(0, 4);
    return person.deathDate ? `${born}–${person.deathDate.slice(0, 4)}` : born;
  }
</script>

<div class="head-row">
  <h1>People</h1>
  <a class="new-link" href="/people/new">+ New person</a>
</div>

{#if data.error}
  <StateBanner variant="error">{data.error}</StateBanner>
{:else if page.items.length === 0}
  <StateBanner variant="info">No people yet. Add someone to start crediting them on movies.</StateBanner>
{:else}
  <ul class="photo-grid" aria-label="People">
    {#each page.items as person (person.id)}
      <li>
        <PersonListRow id={person.id} name={person.name} photoUrl={person.photoUrl} dates={datesFor(person)} />
      </li>
    {/each}
  </ul>
  <nav class="pager" aria-label="Pagination">
    {#if hasPrev}<a href={`/people?offset=${Math.max(0, page.offset - page.limit)}`}>← Previous</a>{/if}
    <span class="count">{page.offset + 1}–{Math.min(page.offset + page.limit, page.total)} of {page.total}</span>
    {#if hasNext}<a href={`/people?offset=${page.offset + page.limit}`}>Next →</a>{/if}
  </nav>
{/if}

<style>
  .head-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--sp-2); }
  .new-link { background: var(--accent); color: var(--accent-contrast); padding: var(--sp-1) var(--sp-2); border-radius: var(--radius); font-weight: 600; text-decoration: none; }
  .pager { display: flex; gap: var(--sp-2); align-items: center; justify-content: center; margin-top: var(--sp-3); }
  .pager .count { color: var(--text-muted); }
</style>
