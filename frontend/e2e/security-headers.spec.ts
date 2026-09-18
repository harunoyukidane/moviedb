import { test, expect } from '@playwright/test';

// V2.3-03: header test. CSP is emitted by SvelteKit itself (kit.csp in
// svelte.config.js) only on a real built/rendered response, so it cannot be
// asserted from a unit test — this has to run against the built BFF.
test('the BFF sets a Content-Security-Policy and the standard security headers on an HTML response', async ({
  page
}) => {
  const response = await page.goto('/movies');
  expect(response).not.toBeNull();

  const headers = response!.headers();
  expect(headers['content-security-policy']).toContain("default-src 'self'");
  expect(headers['content-security-policy']).toContain("object-src 'none'");
  expect(headers['content-security-policy']).toContain("frame-ancestors 'none'");
  expect(headers['x-content-type-options']).toBe('nosniff');
  expect(headers['x-frame-options']).toBe('DENY');
  expect(headers['referrer-policy']).toBe('no-referrer');
  expect(headers['cross-origin-resource-policy']).toBe('same-site');
});
