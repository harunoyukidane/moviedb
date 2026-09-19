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
  let activeIndex = -1;

  $: filtered = (() => {
    const q = query.trim().toLowerCase();
    if (!q) return items;
    return items.filter((i) => i.name.toLowerCase().includes(q) || i.code.toLowerCase().includes(q));
  })();

  // Keep the active option in range as the filtered list shrinks/grows.
  $: if (activeIndex >= filtered.length) activeIndex = filtered.length - 1;

  function optionId(index: number): string {
    return `${id}-option-${index}`;
  }

  function onInput() {
    open = true;
    activeIndex = -1;
    // Typing invalidates the previous selection until a suggestion is picked
    // again, so the hidden input never silently carries a stale code.
    if (nameFor(selectedCode) !== query) selectedCode = null;
  }

  function pick(i: CodeItem) {
    selectedCode = i.code;
    query = i.name;
    open = false;
    activeIndex = -1;
  }

  function onBlur() {
    open = false;
    activeIndex = -1;
    // A non-matching typed value doesn't map to any code; clear the text so
    // the field doesn't look like it holds a selection that was never made.
    if (!selectedCode) query = '';
  }

  function onKeydown(e: KeyboardEvent) {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      if (!open) {
        open = true;
        activeIndex = filtered.length ? 0 : -1;
        return;
      }
      if (filtered.length) activeIndex = (activeIndex + 1) % filtered.length;
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      if (!open) {
        open = true;
        activeIndex = filtered.length ? filtered.length - 1 : -1;
        return;
      }
      if (filtered.length) activeIndex = (activeIndex - 1 + filtered.length) % filtered.length;
    } else if (e.key === 'Enter') {
      if (open && activeIndex >= 0 && activeIndex < filtered.length) {
        e.preventDefault();
        pick(filtered[activeIndex]);
      }
    } else if (e.key === 'Escape') {
      if (open) {
        e.preventDefault();
        open = false;
        activeIndex = -1;
      }
    }
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
    aria-autocomplete="list"
    aria-activedescendant={open && activeIndex >= 0 ? optionId(activeIndex) : undefined}
    {placeholder}
    on:input={onInput}
    on:keydown={onKeydown}
    on:focus={() => (open = true)}
    on:blur={onBlur}
  />
  {#if open && filtered.length}
    <ul id={`${id}-listbox`} class="suggestions" role="listbox">
      {#each filtered as i, index (i.code)}
        <li
          id={optionId(index)}
          role="option"
          aria-selected={index === activeIndex}
          class:active={index === activeIndex}
          on:mousedown|preventDefault={() => pick(i)}
        >
          {i.name}
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
  .suggestions li.active {
    background: var(--surface-2);
  }
</style>
