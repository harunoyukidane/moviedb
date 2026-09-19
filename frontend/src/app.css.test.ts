import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

// jsdom runs no layout, so these pin the CSS rules that make a 300-character
// unbroken token (V2.8-01) wrap instead of widening its container - a
// tripwire against silent deletion, not proof the page lays out correctly.
// The assertion that actually measures the rendered page is the Playwright
// test "a 300-character unbroken name does not widen the person page" in
// `e2e/journey.spec.ts`; if these two ever disagree, believe that one.
const css = readFileSync(join(__dirname, 'app.css'), 'utf-8');

describe('app.css long-word layout guard', () => {
  it('has a global rule allowing unbreakable text to wrap anywhere', () => {
    // `anywhere`, not `break-word`: only `anywhere` feeds the min-content size,
    // which is what lets flex and grid items shrink below the token's width.
    expect(css).toMatch(/overflow-wrap:\s*anywhere/);
  });

  it('lets the detail page grid columns shrink below their content width', () => {
    expect(css).toMatch(/\.detail\s*>\s*\*\s*\{[^}]*min-width:\s*0/);
  });

  it('lets the detail title row heading shrink below its content width', () => {
    expect(css).toMatch(/\.detail-title-row h1\s*\{[^}]*min-width:\s*0/);
  });

  it('lets the detail meta value column shrink below its content width', () => {
    expect(css).toMatch(/\.detail-meta dd\s*\{[^}]*min-width:\s*0/);
  });

  it('lets entity cards shrink below their content width', () => {
    expect(css).toMatch(/\.entity-card\s*\{[^}]*min-width:\s*0/);
  });
});
