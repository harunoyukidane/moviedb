<script lang="ts">
  import type { PageData } from './$types';
  import StateBanner from '$lib/components/StateBanner.svelte';

  export let data: PageData;
  $: page = data.page;
  $: hasPrev = page.offset > 0;
  $: hasNext = page.offset + page.limit < page.total;
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
  <ul class="people-list" aria-label="People">
    {#each page.items as person (person.id)}
      <li>
        <a href={`/people/${person.id}`}>
          <span class="name">{person.name}</span>
          {#if person.birthDate}<span class="dates">{person.birthDate.slice(0, 4)}{person.deathDate ? `–${person.deathDate.slice(0, 4)}` : ''}</span>{/if}
        </a>
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
  .people-list { list-style: none; padding: 0; }
  .people-list li a { display: flex; justify-content: space-between; padding: var(--sp-2); border: 1px solid var(--border); border-radius: var(--radius); margin-bottom: var(--sp-1); text-decoration: none; color: var(--text); background: var(--surface); }
  .name { font-weight: 600; }
  .dates { color: var(--text-muted); }
  .pager { display: flex; gap: var(--sp-2); align-items: center; justify-content: center; margin-top: var(--sp-3); }
  .pager .count { color: var(--text-muted); }
</style>
