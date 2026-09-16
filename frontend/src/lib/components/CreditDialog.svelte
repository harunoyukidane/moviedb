<script lang="ts">
  import { createEventDispatcher, tick } from 'svelte';
  import type { CreditRoleCode } from '$lib/server/types';

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
    noMatch = false;
  }

  function close() {
    open = false;
    previouslyFocused?.focus();
    dispatch('close');
  }

  let debounce: ReturnType<typeof setTimeout>;
  async function onQueryInput() {
    selectedPersonId = '';
    clearTimeout(debounce);
    const q = personQuery.trim();
    if (!q) {
      suggestions = [];
      noMatch = false;
      return;
    }
    debounce = setTimeout(async () => {
      searching = true;
      try {
        const res = await fetch(`/api/people-search?q=${encodeURIComponent(q)}`);
        const data = (await res.json()) as { items: { id: string; name: string }[] };
        suggestions = data.items;
        noMatch = suggestions.length === 0;
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
    noMatch = false;
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
  <!-- svelte-ignore a11y-click-events-have-key-events a11y-no-static-element-interactions a11y-no-noninteractive-element-interactions -->
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

      <form method="POST" action="?/addCredit">
        <div class="field">
          <label for="personQuery">Person</label>
          <input
            id="personQuery"
            bind:this={firstField}
            bind:value={personQuery}
            on:input={onQueryInput}
            autocomplete="off"
            role="combobox"
            aria-expanded={suggestions.length > 0}
            aria-controls="person-suggestions"
            placeholder="Search people…"
          />
          {#if searching}<p class="hint">Searching…</p>{/if}
          {#if suggestions.length}
            <ul id="person-suggestions" class="suggestions" role="listbox">
              {#each suggestions as p (p.id)}
                <li role="option" aria-selected={selectedPersonId === p.id}>
                  <button type="button" on:click={() => pick(p)}>{p.name}</button>
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
            <input id="characterName" name="characterName" bind:value={characterName} required />
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
</style>
