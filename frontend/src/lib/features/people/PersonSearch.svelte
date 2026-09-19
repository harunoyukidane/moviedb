<script lang="ts">
  import { goto } from '$app/navigation';

  interface PersonHit { id: string; name: string }

  export let query: string | null = null;
  // Injectable fetcher/navigator so the debounce + suggestion logic is unit-testable,
  // mirroring SearchBox.svelte's `searchFn` seam.
  export let suggestFn: (q: string) => Promise<PersonHit[]> = defaultSuggest;
  export let navigateFn: (href: string) => void = (href) => void goto(href);
  export let debounceMs = 200;

  const QUERY_MAX_LEN = 100;

  let value = query ?? '';
  let suggestions: PersonHit[] = [];
  let suggestionsOpen = false;
  let activeIndex = -1;

  function optionId(index: number): string {
    return `person-suggestion-${index}`;
  }

  // Keep the active suggestion in range as the list changes.
  $: if (activeIndex >= suggestions.length) activeIndex = suggestions.length - 1;

  async function defaultSuggest(q: string): Promise<PersonHit[]> {
    const res = await fetch(`/api/people-search?q=${encodeURIComponent(q)}`);
    const data = (await res.json()) as { items: PersonHit[] };
    return data.items;
  }

  let debounce: ReturnType<typeof setTimeout>;
  function onInput() {
    clearTimeout(debounce);
    const q = value.trim().slice(0, QUERY_MAX_LEN);
    activeIndex = -1;
    if (!q) {
      suggestions = [];
      suggestionsOpen = false;
      return;
    }
    debounce = setTimeout(async () => {
      try {
        const items = await suggestFn(q);
        // Ignore a stale response if the field changed while this was in flight.
        if (value.trim().slice(0, QUERY_MAX_LEN) !== q) return;
        suggestions = items;
        suggestionsOpen = suggestions.length > 0;
      } catch {
        suggestions = [];
        suggestionsOpen = false;
      }
    }, debounceMs);
  }

  function pick(p: PersonHit) {
    suggestions = [];
    suggestionsOpen = false;
    activeIndex = -1;
    navigateFn(`/people/${p.id}`);
  }

  function onKeydown(e: KeyboardEvent) {
    if (e.key === 'ArrowDown') {
      if (!suggestionsOpen || !suggestions.length) return;
      e.preventDefault();
      activeIndex = (activeIndex + 1) % suggestions.length;
    } else if (e.key === 'ArrowUp') {
      if (!suggestionsOpen || !suggestions.length) return;
      e.preventDefault();
      activeIndex = (activeIndex - 1 + suggestions.length) % suggestions.length;
    } else if (e.key === 'Enter') {
      if (suggestionsOpen && activeIndex >= 0 && activeIndex < suggestions.length) {
        e.preventDefault();
        pick(suggestions[activeIndex]);
      }
    } else if (e.key === 'Escape') {
      suggestionsOpen = false;
      activeIndex = -1;
    }
  }

  // A suggestion button's mousedown fires before the input's blur; preventing
  // its default keeps focus on the input so blur doesn't close the list first.
  function keepFocus(e: MouseEvent) {
    e.preventDefault();
  }

  function onBlur() {
    suggestionsOpen = false;
  }
</script>

<form method="GET" class="person-search" role="search" aria-label="Search people">
  <div class="combobox">
    <label for="person-search-input" class="visually-hidden">Search people by name</label>
    <input
      id="person-search-input"
      type="search"
      name="q"
      placeholder="Search people by name…"
      bind:value
      on:input={onInput}
      on:keydown={onKeydown}
      on:blur={onBlur}
      autocomplete="off"
      maxlength={QUERY_MAX_LEN}
      role="combobox"
      aria-expanded={suggestionsOpen}
      aria-controls="person-suggestions"
      aria-autocomplete="list"
      aria-activedescendant={suggestionsOpen && activeIndex >= 0 ? optionId(activeIndex) : undefined}
    />
    {#if suggestionsOpen}
      <ul id="person-suggestions" class="suggestions" role="listbox">
        {#each suggestions as p, index (p.id)}
          <!-- svelte-ignore a11y-click-events-have-key-events -- keyboard selection (ArrowDown/Enter) is handled on the input above, per the combobox pattern; the option itself stays non-focusable. -->
          <li
            id={optionId(index)}
            role="option"
            aria-selected={index === activeIndex}
            class:active={index === activeIndex}
            on:mousedown={keepFocus}
            on:click={() => pick(p)}
          >
            {p.name}
          </li>
        {/each}
      </ul>
    {/if}
  </div>
  <button type="submit">Search</button>
  {#if query}
    <a class="clear" href="/people">Clear</a>
  {/if}
</form>

<style>
  .person-search {
    display: flex;
    align-items: flex-start;
    gap: var(--sp-1);
    margin-bottom: var(--sp-2);
  }
  .visually-hidden { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
  .combobox {
    position: relative;
    flex: 1 1 auto;
    min-width: 12rem;
  }
  .combobox input {
    width: 100%;
    padding: var(--sp-1);
    border: 1px solid var(--border);
    border-radius: var(--radius);
  }
  .suggestions {
    position: absolute;
    z-index: 10;
    top: 100%;
    left: 0;
    right: 0;
    margin: 4px 0 0;
    padding: 0;
    list-style: none;
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    max-height: 16rem;
    overflow-y: auto;
  }
  .suggestions li {
    padding: var(--sp-1);
    cursor: pointer;
    border-bottom: 1px solid var(--border);
  }
  .suggestions li:last-child { border-bottom: none; }
  .suggestions li:hover,
  .suggestions li.active {
    background: var(--surface-2);
  }
  .person-search button[type='submit'] {
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-radius: var(--radius);
    padding: var(--sp-1) var(--sp-2);
    font-weight: 600;
  }
  .person-search .clear {
    align-self: center;
    color: var(--text-muted);
  }
</style>
