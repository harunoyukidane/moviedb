import adapter from '@sveltejs/adapter-node';
import { vitePreprocess } from '@sveltejs/vite-plugin-svelte';

/** @type {import('@sveltejs/kit').Config} */
const config = {
  preprocess: vitePreprocess(),
  kit: {
    adapter: adapter(),
    // V2.3-01: BFF is the public entry point browsers render HTML from, so it
    // gets the CSP. `mode: 'auto'` makes SvelteKit nonce its own inline scripts
    // instead of falling back to 'unsafe-inline'. img-src allows same-origin +
    // data: only — artwork/photos are proxied through /api/... (§ media proxy),
    // no remote image host is ever referenced.
    csp: {
      mode: 'auto',
      directives: {
        'default-src': ['self'],
        'object-src': ['none'],
        'base-uri': ['self'],
        'frame-ancestors': ['none'],
        'img-src': ['self', 'data:'],
        // ArtworkUpload's progress bar sets an inline `style` attribute with a
        // dynamic width; SvelteKit's auto nonce/hash only covers <style> blocks
        // and script tags, not the style attribute. Scoped to style-src-attr so
        // script-src stays nonce-only — this doesn't relax script execution.
        'style-src-attr': ['unsafe-inline']
      }
    }
  }
};

export default config;
