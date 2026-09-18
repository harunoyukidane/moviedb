import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { describe, expect, it } from 'vitest';

// V2.3-03: turns the one-off injection audit (docs/plans/v2.2/README.md
// "Injection: what was checked") into a standing check. Escaping at render is
// the actual XSS defense; this just asserts nobody quietly opens a DOM sink
// that would bypass it.
const SRC_ROOT = join(__dirname);

function walk(dir: string): string[] {
  const entries = readdirSync(dir);
  const files: string[] = [];
  for (const entry of entries) {
    const full = join(dir, entry);
    const stats = statSync(full);
    if (stats.isDirectory()) {
      files.push(...walk(full));
    } else {
      files.push(full);
    }
  }
  return files;
}

const allFiles = walk(SRC_ROOT).filter((f) => !f.includes(`${join('node_modules')}`));
const svelteFiles = allFiles.filter((f) => f.endsWith('.svelte'));
const sourceFiles = allFiles.filter(
  (f) =>
    (f.endsWith('.ts') || f.endsWith('.js') || f.endsWith('.svelte')) &&
    !f.endsWith('.test.ts') &&
    !f.endsWith('source-guard.test.ts')
);

describe('source guard (V2.3-03)', () => {
  it('no .svelte file uses {@html}', () => {
    const offenders = svelteFiles.filter((f) => readFileSync(f, 'utf-8').includes('{@html'));
    expect(offenders.map((f) => relative(SRC_ROOT, f))).toEqual([]);
  });

  it('no frontend source uses innerHTML, outerHTML, eval(, or new Function', () => {
    const dangerousPatterns = [/\.innerHTML\b/, /\.outerHTML\b/, /\beval\s*\(/, /\bnew Function\s*\(/];
    const offenders = sourceFiles.filter((f) => {
      const content = readFileSync(f, 'utf-8');
      return dangerousPatterns.some((pattern) => pattern.test(content));
    });
    expect(offenders.map((f) => relative(SRC_ROOT, f))).toEqual([]);
  });
});
