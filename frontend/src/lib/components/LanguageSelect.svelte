<script lang="ts">
  import type { LanguageCode } from '$lib/server/types';

  export let languages: LanguageCode[] = [];
  export let selected: string | null | undefined = null;
  export let name = 'originalLanguage';
  export let id = 'originalLanguage';
  export let label = 'Original language';

  function nameFor(code: string | null): string {
    if (!code) return '';
    return languages.find((l) => l.code === code)?.name ?? code;
  }

  let selectedCode: string | null = selected ?? null;
  let query = nameFor(selectedCode);
  let open = false;

  $: filtered = (() => {
    const q = query.trim().toLowerCase();
    if (!q) return languages;
    return languages.filter(
      (l) => l.name.toLowerCase().includes(q) || l.code.toLowerCase().includes(q)
    );
  })();

  function onInput() {
    open = true;
    // Typing invalidates the previous selection until a suggestion is picked
    // again, so the hidden input never silently carries a stale code.
    if (nameFor(selectedCode) !== query) selectedCode = null;
  }

  function pick(l: LanguageCode) {
    selectedCode = l.code;
    query = l.name;
    open = false;
  }

  function onBlur() {
    open = false;
    // A non-matching typed value doesn't map to any code; clear the text so
    // the field doesn't look like it holds a selection that was never made.
    if (!selectedCode) query = '';
  }
</script>

<div class="language-select">
  <label for={id}>{label}</label>
  <input
    {id}
    bind:value={query}
    autocomplete="off"
    role="combobox"
    aria-expanded={open && filtered.length > 0}
    aria-controls={`${id}-listbox`}
    placeholder="Search languages…"
    on:input={onInput}
    on:focus={() => (open = true)}
    on:blur={onBlur}
  />
  {#if open && filtered.length}
    <ul id={`${id}-listbox`} class="suggestions" role="listbox">
      {#each filtered as l (l.code)}
        <li role="option" aria-selected={selectedCode === l.code}>
          <button type="button" on:mousedown|preventDefault={() => pick(l)}>{l.name}</button>
        </li>
      {/each}
    </ul>
  {/if}
  <input type="hidden" {name} value={selectedCode ?? ''} />
</div>

<style>
  .language-select {
    position: relative;
  }
  .suggestions {
    position: absolute;
    z-index: 10;
    background: var(--surface);
    width: 100%;
  }
</style>
