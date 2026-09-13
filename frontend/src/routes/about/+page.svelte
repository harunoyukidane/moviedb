<script lang="ts">
  // Static informational page: TMDB attribution (§12.5) + architecture summary.
</script>

<svelte:head><title>About · MovieDB</title></svelte:head>

<h1>About MovieDB</h1>

<section aria-label="Architecture summary">
  <h2>Architecture</h2>
  <p>
    MovieDB is a small movie catalogue built as two independent services. A
    <strong>Catalogue</strong> service owns movies, credits, genres and artwork and
    exposes a GraphQL API; a <strong>People</strong> service owns people and exposes
    an internal gRPC API. The browser talks only to a SvelteKit server-side
    backend-for-frontend (BFF), which calls GraphQL server-side; artwork bytes are
    served over dedicated HTTP media endpoints. Each service has its own PostgreSQL
    database.
  </p>
</section>

<section aria-label="Attribution" class="attribution">
  <h2>Data attribution</h2>
  <!-- The attribution notice must be present and the TMDB mark less prominent
       than the app brand (§12.5). Text is the exact required wording. -->
  <p class="tmdb-logo" aria-hidden="true">TMDB</p>
  <p class="tmdb-notice">
    This product uses the TMDB API but is not endorsed or certified by TMDB.
  </p>
  <p class="muted">
    Movie and person data, and posters, are sourced from
    <a href="https://www.themoviedb.org" target="_blank" rel="noopener noreferrer">The Movie Database (TMDB)</a>
    at setup time. After seeding, the application has no runtime dependency on TMDB.
  </p>
</section>

<style>
  section { margin-bottom: var(--sp-4); }
  .attribution { border-top: 1px solid var(--border); padding-top: var(--sp-2); }
  /* Deliberately understated: smaller than the app brand in the header. */
  .tmdb-logo {
    display: inline-block;
    font-weight: 700;
    letter-spacing: 0.08em;
    color: var(--text-muted);
    background: var(--surface-2);
    border: 1px solid var(--border);
    border-radius: 4px;
    padding: 2px 8px;
    font-size: 0.9rem;
    margin: 0 0 var(--sp-1);
  }
  .tmdb-notice { font-size: 0.9rem; color: var(--text-muted); margin: 0 0 var(--sp-1); }
  .muted { color: var(--text-muted); }
</style>
