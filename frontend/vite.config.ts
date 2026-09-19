import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [sveltekit()],
  build: {
    rollupOptions: {
      output: {
        // The Svelte/SvelteKit runtime (scheduler, stores, the client router, etc.)
        // ships as ~8 separate tiny chunks by default, all of which every route
        // needs on first load - that's several extra network round trips for
        // files that are never independently useful. Bundling every node_modules
        // dependency into one vendor chunk collapses those into a single request
        // and shortens the critical-path chain Lighthouse measures.
        manualChunks(id) {
          if (id.includes('node_modules')) return 'vendor';
        }
      }
    }
  },
  resolve: {
    // Resolve Svelte's browser build so @testing-library/svelte can mount components
    // under jsdom during tests.
    conditions: process.env.VITEST ? ['browser'] : []
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./vitest-setup.ts'],
    include: ['src/**/*.{test,spec}.{js,ts}']
  }
});
