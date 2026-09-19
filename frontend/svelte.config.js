import adapter from '@sveltejs/adapter-node';
import { vitePreprocess } from '@sveltejs/vite-plugin-svelte';

/** @type {import('@sveltejs/kit').Config} */
const config = {
  preprocess: vitePreprocess(),
  kit: {
    adapter: adapter(),
    // Render-blocking CSS fix: per-route/component CSS chunks under this size get
    // inlined as a <style> tag in the SSR'd HTML instead of an external
    // render-blocking <link>. Covers every chunk this app currently produces
    // (largest observed so far is the root layout's ~6KB); CSP `mode: 'auto'`
    // already hashes/nonces inlined <style> the same way it does inline <script>,
    // so no CSP directive changes are needed alongside this.
    inlineStyleThreshold: 8192,
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
