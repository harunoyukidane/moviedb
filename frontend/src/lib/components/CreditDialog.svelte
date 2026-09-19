<script lang="ts">
  import { createEventDispatcher, tick } from 'svelte';
  import { enhance } from '$app/forms';
  import type { CreditRoleCode } from '$lib/server/types';
  import CharCounter from './CharCounter.svelte';

  const CHARACTER_NAME_MAX = 300;

  export let open = false;
  export let roles: CreditRoleCode[] = [];

  const dispatch = createEventDispatcher<{ close: void }>();

  let dialogEl: HTMLDivElement | null = null;
  let firstField: HTMLInputElement | null = null;
  let previouslyFocused: HTMLElement | null = null;

  // form state
  let personQuery = '';
  let selectedPersonId = '';
  let selectedPersonName = '';
  let roleCode = '';
  let characterName = '';
  let billingOrder = '';
  let suggestions: { id: string; name: string }[] = [];
  let searching = false;
  let noMatch = false;
  let searchError = false;
  let suggestionsOpen = false;
  let activeIndex = -1;

  function suggestionId(index: number): string {
    return `person-suggestion-${index}`;
  }

  $: selectedRole = roles.find((r) => r.code === roleCode) ?? null;
  $: isCast = selectedRole?.category === 'CAST';

  $: if (open) void onOpen();

  async function onOpen() {
    previouslyFocused = document.activeElement as HTMLElement | null;
    resetForm();
    await tick();
    firstField?.focus();
  }

  function resetForm() {
    personQuery = '';
    selectedPersonId = '';
    selectedPersonName = '';
    roleCode = '';
    characterName = '';
    billingOrder = '';
    suggestions = [];
    suggestionsOpen = false;
    activeIndex = -1;
    noMatch = false;
    searchError = false;
  }

  function close() {
    open = false;
    previouslyFocused?.focus();
    dispatch('close');
  }

  // Keep the active suggestion in range as the list changes.
  $: if (activeIndex >= suggestions.length) activeIndex = suggestions.length - 1;

  let debounce: ReturnType<typeof setTimeout>;
  async function onQueryInput() {
    selectedPersonId = '';
    activeIndex = -1;
    clearTimeout(debounce);
    const q = personQuery.trim();
    if (!q) {
      suggestions = [];
      suggestionsOpen = false;
      noMatch = false;
      searchError = false;
      return;
    }
    debounce = setTimeout(async () => {
      searching = true;
      searchError = false;
      try {
        const res = await fetch(`/api/people-search?q=${encodeURIComponent(q)}`);
        const data = (await res.json()) as { items: { id: string; name: string }[] };
        suggestions = data.items;
        suggestionsOpen = suggestions.length > 0;
        noMatch = suggestions.length === 0;
      } catch {
        suggestions = [];
        suggestionsOpen = false;
        noMatch = false;
        searchError = true;
      } finally {
        searching = false;
      }
    }, 200);
  }

  function pick(p: { id: string; name: string }) {
    selectedPersonId = p.id;
    selectedPersonName = p.name;
    personQuery = p.name;
    suggestions = [];
    suggestionsOpen = false;
    activeIndex = -1;
    noMatch = false;
  }

  function onQueryKeydown(e: KeyboardEvent) {
    if (e.key === 'ArrowDown') {
      if (!suggestions.length) return;
      e.preventDefault();
      const wasOpen = suggestionsOpen;
      suggestionsOpen = true;
      activeIndex = wasOpen ? (activeIndex + 1) % suggestions.length : 0;
    } else if (e.key === 'ArrowUp') {
      if (!suggestions.length) return;
      e.preventDefault();
      const wasOpen = suggestionsOpen;
      suggestionsOpen = true;
      activeIndex = wasOpen ? (activeIndex - 1 + suggestions.length) % suggestions.length : suggestions.length - 1;
    } else if (e.key === 'Enter') {
      if (suggestionsOpen && activeIndex >= 0 && activeIndex < suggestions.length) {
        e.preventDefault();
        pick(suggestions[activeIndex]);
      }
    } else if (e.key === 'Escape' && suggestionsOpen) {
      // Close just the suggestion list; the window-level handler below
      // handles the dialog's own Escape-to-close.
      e.stopPropagation();
      suggestionsOpen = false;
      activeIndex = -1;
    }
  }

  function onKeydown(e: KeyboardEvent) {
    if (open && e.key === 'Escape') {
      e.preventDefault();
      close();
    }
  }

  // client-side guard so the dialog gives immediate feedback; server re-validates
  $: canSubmit = !!selectedPersonId && !!roleCode && (!isCast || characterName.trim().length > 0);
</script>

<svelte:window on:keydown={onKeydown} />

{#if open}
  <!-- svelte-ignore a11y-click-events-have-key-events -- dismiss-on-click backdrop; Escape is handled by the window keydown listener above, not this element. -->
  <!-- svelte-ignore a11y-no-static-element-interactions -- role="presentation" backdrop is intentionally non-interactive to assistive tech; the click only dismisses, it carries no content or focusable behavior of its own. -->
  <!-- svelte-ignore a11y-no-noninteractive-element-interactions -- same backdrop-dismiss pattern; role="presentation" removes it from the accessibility tree by design. -->
  <div class="overlay" role="presentation" on:click={close}>
    <div
      class="dialog"
      bind:this={dialogEl}
      role="dialog"
      aria-modal="true"
      aria-labelledby="credit-title"
      on:click|stopPropagation
      on:keydown|stopPropagation
    >
      <h2 id="credit-title">Add credit</h2>

      <form
        method="POST"
        action="?/addCredit"
        use:enhance={() => async ({ update }) => {
          await update();
          close();
        }}
      >
        <div class="field">
          <label for="personQuery">Person</label>
          <input
            id="personQuery"
            bind:this={firstField}
            bind:value={personQuery}
            on:input={onQueryInput}
            on:keydown={onQueryKeydown}
            autocomplete="off"
            role="combobox"
            aria-expanded={suggestionsOpen}
            aria-controls="person-suggestions"
            aria-autocomplete="list"
            aria-activedescendant={suggestionsOpen && activeIndex >= 0 ? suggestionId(activeIndex) : undefined}
            placeholder="Search people…"
          />
          {#if searching}<p class="hint">Searching…</p>{/if}
          {#if suggestionsOpen && suggestions.length}
            <ul id="person-suggestions" class="suggestions" role="listbox">
              {#each suggestions as p, index (p.id)}
                <li
                  id={suggestionId(index)}
                  role="option"
                  aria-selected={index === activeIndex}
                  class:active={index === activeIndex}
                  on:mousedown|preventDefault={() => pick(p)}
                >
                  {p.name}
                </li>
              {/each}
            </ul>
          {/if}
          {#if noMatch}
            <p class="hint">
              No matching person. <a href="/people/new" target="_blank" rel="noopener">Create the person first</a>,
              then search again. We won't create a duplicate for you.
            </p>
          {/if}
          {#if searchError}
            <p class="hint" role="alert">Couldn't search people just now. Try again in a moment.</p>
          {/if}
          <input type="hidden" name="personId" value={selectedPersonId} />
        </div>

        <div class="field">
          <label for="roleCode">Role</label>
          <select id="roleCode" name="roleCode" bind:value={roleCode} required>
            <option value="" disabled>Select a role…</option>
            {#each roles as r (r.code)}
              <option value={r.code}>{r.title} ({r.category})</option>
            {/each}
          </select>
        </div>

        {#if isCast}
          <div class="field">
            <label for="characterName">Character name *</label>
            <input
              id="characterName"
              name="characterName"
              maxlength={CHARACTER_NAME_MAX}
              bind:value={characterName}
              required
            />
            <CharCounter value={characterName} max={CHARACTER_NAME_MAX} />
          </div>
        {/if}

        <div class="field">
          <label for="billingOrder">Billing order (optional)</label>
          <input id="billingOrder" name="billingOrder" type="number" min="0" bind:value={billingOrder} />
        </div>

        <div class="actions">
          <button type="button" on:click={close}>Cancel</button>
          <button type="submit" class="primary" disabled={!canSubmit}>Add credit</button>
        </div>
      </form>
    </div>
  </div>
{/if}

<style>
  .dialog { max-width: 520px; }
  .hint { font-size: 0.875rem; }
  .actions { margin-top: var(--sp-2); }
  .suggestions li.active { background: var(--surface-2); }
</style>
