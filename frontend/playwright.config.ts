import { defineConfig, devices } from '@playwright/test';

// E2E runs against a running app. In CI/local, start the stack first
// (`./scripts/setup.sh`) and the SvelteKit BFF (`npm run build && npm run preview`),
// then `BASE_URL=http://localhost:4173 npm run test:e2e`.
const baseURL = process.env.BASE_URL ?? 'http://localhost:4173';

export default defineConfig({
  testDir: 'e2e',
  timeout: 30_000,
  fullyParallel: false,
  retries: process.env.CI ? 1 : 0,
  reporter: 'list',
  use: {
    baseURL,
    trace: 'on-first-retry'
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }]
});
