<script lang="ts">
  // Renders every thrown error (error(404, ...), error(503, ...), an
  // unhandled exception) inside the app shell instead of SvelteKit's
  // unstyled fallback page (F18, V2.2-09).
  import { page } from '$app/stores';
  import StateBanner from '$lib/components/StateBanner.svelte';

  $: status = $page.status;
  $: message = $page.error?.message ?? 'Something went wrong.';
  $: heading = status === 404 ? 'Not found' : status >= 500 ? 'Service unavailable' : 'Something went wrong';
</script>

<svelte:head>
  <title>{heading} · MovieDB</title>
</svelte:head>

<section class="error-page" aria-labelledby="error-heading">
  <h1 id="error-heading">{heading}</h1>
  <StateBanner variant="error">{message}</StateBanner>
  <p><a href="/movies">Back to movies</a></p>
</section>

<style>
  .error-page {
    padding: var(--sp-4) 0;
  }
  .error-page h1 {
    margin-top: 0;
  }
  .error-page p {
    margin-top: var(--sp-2);
  }
</style>
