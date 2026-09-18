<script lang="ts">
  import StateBanner from './StateBanner.svelte';

  interface MovieHit { id: string; title: string; releaseDate?: string | null; matchedPersonNames: string[] }
  interface PersonHit { id: string; name: string }
  interface Results { movies: MovieHit[]; people: PersonHit[]; error?: string }

  // Injectable fetcher so the debounce + token-guard logic is unit-testable.
  export let searchFn: (q: string, signal: AbortSignal) => Promise<Results> = defaultFetch;
  export let debounceMs = 300;

  // Mirrors the server's query bound (SearchUseCases.QUERY_MAX_LEN) so a long
  // paste is clamped client-side instead of round-tripping to a BAD_USER_INPUT
  // the search box has no field to highlight (F19).
  const QUERY_MAX_LEN = 100;

  let query = '';
  let results: Results | null = null;
  let loading = false;
  let errorMessage: string | null = null;

  let debounceTimer: ReturnType<typeof setTimeout> | undefined;
  // Monotonic token: only the newest request may commit its results.
  let latestToken = 0;
  let inflight: AbortController | null = null;

  async function defaultFetch(q: string, signal: AbortSignal): Promise<Results> {
    const res = await fetch(`/api/search?q=${encodeURIComponent(q)}`, { signal });
    return (await res.json()) as Results;
  }

  function onInput() {
    clearTimeout(debounceTimer);
    const q = query.trim().slice(0, QUERY_MAX_LEN);
    if (q.length === 0) {
      results = null;
      loading = false;
      errorMessage = null;
      inflight?.abort();
      return;
    }
    debounceTimer = setTimeout(() => void run(q), debounceMs);
  }

  async function run(q: string) {
    const token = ++latestToken;
    // cancel any in-flight request so a stale response cannot win
    inflight?.abort();
    inflight = new AbortController();
    loading = true;
    errorMessage = null;
    try {
      const r = await searchFn(q, inflight.signal);
      // token guard: ignore if a newer query started while this was in flight
      if (token !== latestToken) return;
      results = r;
      errorMessage = r.error ?? null;
    } catch (e) {
      if ((e as Error)?.name === 'AbortError') return;
      if (token !== latestToken) return;
      errorMessage = 'Search failed. Please try again.';
    } finally {
      if (token === latestToken) loading = false;
    }
  }

  $: hasResults = !!results && (results.movies.length > 0 || results.people.length > 0);
  $: isEmpty = !!results && !loading && !hasResults && query.trim().length > 0;
</script>

<div class="search">
  <label for="search-input" class="visually-hidden">Search movies and people</label>
  <input
    id="search-input"
    type="search"
    placeholder="Search movies and people…"
    bind:value={query}
    on:input={onInput}
    autocomplete="off"
    maxlength={QUERY_MAX_LEN}
  />

  {#if loading}
    <p class="status" role="status" data-testid="search-loading">Searching…</p>
  {/if}

  {#if errorMessage}
    <StateBanner variant="error">{errorMessage}</StateBanner>
  {:else if isEmpty}
    <StateBanner variant="info">No matches for “{query}”.</StateBanner>
  {:else if hasResults && results}
    <div class="results" data-testid="search-results">
      {#if results.movies.length}
        <section aria-label="Movie results">
          <h3>Movies</h3>
          <ul>
            {#each results.movies as m (m.id)}
              <li>
                <a href={`/movies/${m.id}`}>{m.title}</a>
                {#if m.releaseDate}<span class="year">({m.releaseDate.slice(0, 4)})</span>{/if}
                {#if m.matchedPersonNames.length}
                  <span class="matched">— with {m.matchedPersonNames.join(', ')}</span>
                {/if}
              </li>
            {/each}
          </ul>
        </section>
      {/if}
      {#if results.people.length}
        <section aria-label="People results">
          <h3>People</h3>
          <ul>
            {#each results.people as p (p.id)}
              <li><a href={`/people/${p.id}`}>{p.name}</a></li>
            {/each}
          </ul>
        </section>
      {/if}
    </div>
  {/if}
</div>

<style>
  .search { margin-bottom: var(--sp-3); }
  .visually-hidden { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
  .status { color: var(--text-muted); }
  .results section { margin-top: var(--sp-2); }
  .results h3 { margin: 0 0 var(--sp-1); color: var(--text-muted); font-size: 0.875rem; text-transform: uppercase; letter-spacing: 0.05em; }
  .results ul { list-style: none; padding: 0; margin: 0; }
  .results li { padding: var(--sp-1) 0; border-bottom: 1px solid var(--border); }
  .year { color: var(--text-muted); }
  .matched { color: var(--accent); font-size: 0.875rem; }
</style>
