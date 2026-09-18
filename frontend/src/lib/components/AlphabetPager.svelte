<script lang="ts">
  const LETTERS = Array.from({ length: 26 }, (_, i) => String.fromCharCode(65 + i));

  export let hrefFor: (letter: string) => string;
  export let activeLetter: string | null = null;
</script>

<nav class="alphabet-pager" aria-label="Jump to letter">
  {#each LETTERS as letter (letter)}
    <a
      href={hrefFor(letter)}
      class:active={letter === activeLetter}
      aria-current={letter === activeLetter ? 'true' : undefined}
    >
      {letter}
    </a>
  {/each}
</nav>

<style>
  .alphabet-pager {
    display: flex;
    flex-wrap: wrap;
    gap: 4px;
    justify-content: center;
    margin-top: var(--sp-2);
  }
  .alphabet-pager a {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 1.75rem;
    height: 1.75rem;
    padding: 0 4px;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    color: var(--text-muted);
    font-size: 0.85rem;
    font-weight: 600;
    text-decoration: none;
  }
  .alphabet-pager a:hover {
    background: var(--surface-2);
    color: var(--fg, inherit);
  }
  .alphabet-pager a.active {
    background: var(--accent);
    border-color: var(--accent);
    color: var(--accent-contrast);
  }
</style>
