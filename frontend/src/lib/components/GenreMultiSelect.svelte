<script lang="ts">
  import type { GenreCode } from '$lib/server/types';

  export let genres: GenreCode[] = [];
  export let selected: string[] = [];
  export let name = 'genreCodes';

  function toggle(code: string) {
    selected = selected.includes(code) ? selected.filter((c) => c !== code) : [...selected, code];
  }

  $: selectedGenres = genres.filter((g) => selected.includes(g.code));
</script>

<fieldset class="genre-select">
  <legend>Genres</legend>

  {#if selectedGenres.length}
    <ul class="tags" aria-label="Selected genres">
      {#each selectedGenres as g (g.code)}
        <li class="tag">
          {g.title}
          <button type="button" aria-label={`Remove ${g.title}`} on:click={() => toggle(g.code)}>×</button>
        </li>
      {/each}
    </ul>
  {/if}

  <div class="options" role="group" aria-label="Available genres">
    {#each genres as g (g.code)}
      <label class="option">
        <input type="checkbox" value={g.code} checked={selected.includes(g.code)} on:change={() => toggle(g.code)} />
        {g.title}
      </label>
    {/each}
  </div>

  <!-- hidden inputs carry the selection to the server form action -->
  {#each selected as code (code)}
    <input type="hidden" {name} value={code} />
  {/each}
</fieldset>

<style>
  .genre-select { border: 1px solid var(--border); border-radius: var(--radius); padding: var(--sp-2); }
  legend { font-weight: 600; padding: 0 var(--sp-1); }
  .tags { list-style: none; padding: 0; display: flex; flex-wrap: wrap; gap: var(--sp-1); margin: 0 0 var(--sp-2); }
  .tag { display: inline-flex; align-items: center; gap: 4px; background: var(--accent); color: var(--accent-contrast); border-radius: 999px; padding: 2px var(--sp-1); font-size: 0.875rem; }
  .tag button { background: none; border: none; color: var(--accent-contrast); font-size: 1rem; padding: 0 2px; line-height: 1; }
  .options { display: flex; flex-wrap: wrap; gap: var(--sp-1) var(--sp-2); }
  .option { display: inline-flex; align-items: center; gap: 6px; font-weight: 400; margin: 0; }
  .option input { width: auto; }
</style>
