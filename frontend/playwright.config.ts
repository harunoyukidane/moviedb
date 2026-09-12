import type { PlaywrightTestConfig } from '@playwright/test';

// E2E tests are authored in phase 8. This stub records the intended config so the
// tooling slot exists now. Install @playwright/test when phase 8 lands.
const config: PlaywrightTestConfig = {
  webServer: {
    command: 'npm run build && npm run preview',
    port: 4173
  },
  testDir: 'e2e'
};

export default config;
