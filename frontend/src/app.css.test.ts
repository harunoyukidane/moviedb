import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

// jsdom does not run layout, so a real "does the box exceed the viewport"
// assertion isn't possible here - instead this pins the CSS rules that make a
// 300-character unbroken token (V2.8-01) wrap instead of widening its
// container. If any of these regress, the rule text below stops matching and
// the test fails without needing a real browser.
const css = readFileSync(join(__dirname, 'app.css'), 'utf-8');

const LONG_UNBROKEN_TOKEN = 'a'.repeat(300);

describe('app.css long-word layout guard', () => {
  it('has a global rule allowing unbreakable text to wrap anywhere', () => {
    expect(LONG_UNBROKEN_TOKEN).toHaveLength(300);
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
