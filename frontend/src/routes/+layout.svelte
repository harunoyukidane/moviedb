<script lang="ts">
  import '../app.css';
  import { page } from '$app/stores';
</script>

<a href="#main" class="skip-link">Skip to content</a>
<header class="site-header">
  <div class="container header-row">
    <a class="brand" href="/movies">🎬 MovieDB</a>
    <nav aria-label="Primary">
      <a href="/movies" aria-current={$page.url.pathname.startsWith('/movies') ? 'page' : undefined}>Movies</a>
      <a href="/people" aria-current={$page.url.pathname.startsWith('/people') ? 'page' : undefined}>People</a>
    </nav>
  </div>
</header>

<main id="main" class="container">
  <slot />
</main>

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
</style>
