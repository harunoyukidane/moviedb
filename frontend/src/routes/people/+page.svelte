<script lang="ts">
  import type { PageData } from './$types';
  import StateBanner from '$lib/components/StateBanner.svelte';
  import AlphabetPager from '$lib/components/AlphabetPager.svelte';
  import PersonListRow from '$lib/features/people/PersonListRow.svelte';
  import PersonSearch from '$lib/features/people/PersonSearch.svelte';

  export let data: PageData;
  $: page = data.page;
  $: hasPrev = page.offset > 0;
  $: hasNext = page.offset + page.limit < page.total;

  function datesFor(person: { birthDate?: string | null; deathDate?: string | null }): string | null {
    if (!person.birthDate) return null;
    const born = person.birthDate.slice(0, 4);
    return person.deathDate ? `${born}–${person.deathDate.slice(0, 4)}` : born;
  }

  /** Pager hrefs carry the active search query so paging never drops it. */
  function pagerHref(offset: number, query: string | null): string {
    const params = new URLSearchParams();
    if (query) params.set('q', query);
    if (offset > 0) params.set('offset', String(offset));
    const qs = params.toString();
    return qs ? `/people?${qs}` : '/people';
  }

  $: prevHref = pagerHref(Math.max(0, page.offset - page.limit), data.query);
  $: nextHref = pagerHref(page.offset + page.limit, data.query);

  /** Letter hrefs carry the active search query but drop offset - the server recomputes it from the letter. */
  function letterHref(letter: string, query: string | null): string {
    const params = new URLSearchParams();
    if (query) params.set('q', query);
    params.set('letter', letter);
    return `/people?${params.toString()}`;
  }

  $: activeLetter = /^[A-Za-z]/.test(page.items[0]?.name ?? '') ? page.items[0].name[0].toUpperCase() : null;
</script>

<svelte:head><title>People · MovieDB</title></svelte:head>

<div class="head-row">
  <h1>People</h1>
  <a class="new-link" href="/people/new">+ New person</a>
</div>

<PersonSearch query={data.query} />

{#if data.error}
  <StateBanner variant="error">{data.error}</StateBanner>
{:else if page.items.length === 0}
  <StateBanner variant="info">
    {#if data.query}
      No people match “{data.query}”.
    {:else}
      No people yet. Add someone to start crediting them on movies.
    {/if}
  </StateBanner>
{:else}
  <ul class="photo-grid" aria-label="People">
    {#each page.items as person, i (person.id)}
      <li>
        <PersonListRow id={person.id} name={person.name} photoUrl={person.photoUrl} dates={datesFor(person)} index={i} />
      </li>
    {/each}
  </ul>
  <nav class="pager" aria-label="Pagination">
    {#if hasPrev}<a href={prevHref}>← Previous</a>{/if}
    <span class="count">{page.offset + 1}–{Math.min(page.offset + page.limit, page.total)} of {page.total}</span>
    {#if hasNext}<a href={nextHref}>Next →</a>{/if}
  </nav>
  <AlphabetPager hrefFor={(letter) => letterHref(letter, data.query)} {activeLetter} />
{/if}

<style>
  .head-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: var(--sp-2); }
  .new-link { background: var(--accent); color: var(--accent-contrast); padding: var(--sp-1) var(--sp-2); border-radius: var(--radius); font-weight: 600; text-decoration: none; }
</style>
