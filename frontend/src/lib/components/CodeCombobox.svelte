<script lang="ts">
  // Shared searchable code/name combobox (V2.2-10), generalized out of what
  // was LanguageSelect - client-side filtering over an already-loaded list,
  // a hidden input carrying the code, typing invalidates a stale selection,
  // and blur clears unmatched text. LanguageSelect and CountrySelect are both
  // thin wrappers around this.
  interface CodeItem {
    code: string;
    name: string;
  }

  export let items: CodeItem[] = [];
  export let selected: string | null | undefined = null;
  export let name: string;
  export let id: string;
  export let label: string;
  export let placeholder = 'Search…';

  function nameFor(code: string | null): string {
    if (!code) return '';
    return items.find((i) => i.code === code)?.name ?? code;
  }

  let selectedCode: string | null = selected ?? null;
  let query = nameFor(selectedCode);
  let open = false;

  $: filtered = (() => {
    const q = query.trim().toLowerCase();
    if (!q) return items;
    return items.filter((i) => i.name.toLowerCase().includes(q) || i.code.toLowerCase().includes(q));
  })();

  function onInput() {
    open = true;
    // Typing invalidates the previous selection until a suggestion is picked
    // again, so the hidden input never silently carries a stale code.
    if (nameFor(selectedCode) !== query) selectedCode = null;
  }

  function pick(i: CodeItem) {
    selectedCode = i.code;
    query = i.name;
    open = false;
  }

  function onBlur() {
    open = false;
    // A non-matching typed value doesn't map to any code; clear the text so
    // the field doesn't look like it holds a selection that was never made.
    if (!selectedCode) query = '';
  }
</script>

<div class="code-combobox">
  <label for={id}>{label}</label>
  <input
    {id}
    bind:value={query}
    autocomplete="off"
    role="combobox"
    aria-expanded={open && filtered.length > 0}
    aria-controls={`${id}-listbox`}
    {placeholder}
    on:input={onInput}
    on:focus={() => (open = true)}
    on:blur={onBlur}
  />
  {#if open && filtered.length}
    <ul id={`${id}-listbox`} class="suggestions" role="listbox">
      {#each filtered as i (i.code)}
        <li role="option" aria-selected={selectedCode === i.code}>
          <button type="button" on:mousedown|preventDefault={() => pick(i)}>{i.name}</button>
        </li>
      {/each}
    </ul>
  {/if}
  <input type="hidden" {name} value={selectedCode ?? ''} />
</div>

<style>
  .code-combobox {
    position: relative;
  }
  .suggestions {
    position: absolute;
    z-index: 10;
    background: var(--surface);
    width: 100%;
  }
</style>
