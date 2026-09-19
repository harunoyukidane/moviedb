<script lang="ts">
  import '../app.css';
  import { page } from '$app/stores';
  import { afterNavigate } from '$app/navigation';
  import { previousPageUrl } from '$lib/stores/navigation';

  // Tracks the page navigated away from, so BackLink can return the user to
  // where they actually came from (a filtered list, a movie/person page)
  // rather than a fixed "all movies"/"all people" destination (V2.8-06).
  afterNavigate(({ from }) => {
    if (from?.url) previousPageUrl.set(from.url.pathname + from.url.search);
  });
</script>

<svelte:head>
  <meta name="description" content="Browse and manage a catalogue of movies, cast and crew." />
</svelte:head>

<a href="#main" class="skip-link">Skip to content</a>
<header class="site-header">
  <div class="container header-row">
    <a class="brand" href="/movies">🎬 MovieDB</a>
    <nav aria-label="Primary">
      <a href="/movies" aria-current={$page.url.pathname.startsWith('/movies') ? 'page' : undefined}>Movies</a>
      <a href="/people" aria-current={$page.url.pathname.startsWith('/people') ? 'page' : undefined}>People</a>
      <a href="/about" aria-current={$page.url.pathname.startsWith('/about') ? 'page' : undefined}>About</a>
    </nav>
  </div>
</header>

<main id="main" class="container">
  <slot />
</main>

<footer class="site-footer">
  <div class="container">
    <p>This product uses the TMDB API but is not endorsed or certified by TMDB.</p>
  </div>
</footer>

<style>
  .skip-link {
    position: absolute;
    left: -9999px;
  }
  .skip-link:focus {
    left: var(--sp-2);
    top: var(--sp-1);
    background: var(--surface-2);
    padding: var(--sp-1) var(--sp-2);
    border-radius: var(--radius);
    z-index: 10;
  }
  .site-header {
    border-bottom: 1px solid var(--border);
    background: var(--surface);
  }
  .header-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-top: var(--sp-2);
    padding-bottom: var(--sp-2);
  }
  .brand {
    font-weight: 700;
    font-size: 1.25rem;
    color: var(--text);
    text-decoration: none;
  }
  nav a {
    margin-left: var(--sp-2);
    color: var(--text-muted);
    text-decoration: none;
  }
  nav a[aria-current='page'] {
    color: var(--accent);
    font-weight: 600;
  }
  .site-footer {
    border-top: 1px solid var(--border);
    margin-top: var(--sp-5);
    color: var(--text-muted);
    font-size: 0.85rem;
  }
  .site-footer p { margin: var(--sp-2) 0; }
</style>
