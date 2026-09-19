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

// V2.6-02: commit 0b27e95 suppressed the exact a11y-click-events-have-key-events
// / a11y-no-noninteractive-element-interactions warnings that would have caught
// the V2.6-01 keyboard lockout, via a blanket two-rule `svelte-ignore`. This
// guard doesn't forbid `svelte-ignore a11y-*` outright - a dismiss-on-click modal
// overlay is a legitimate case - but it forces every one to name a single rule
// and carry a `--` justification, so a new blanket suppression can't land quietly.
describe('a11y suppression guard (V2.6-02)', () => {
  const ignoreCommentPattern = /<!--\s*svelte-ignore\s+([^-][^>]*?)-->/g;

  it('every svelte-ignore covering an a11y-* rule names exactly one rule and carries a justification', () => {
    const offenders: string[] = [];
    for (const file of svelteFiles) {
      const content = readFileSync(file, 'utf-8');
      for (const match of content.matchAll(ignoreCommentPattern)) {
        const body = match[1].trim();
        const [rulesPart, ...justificationParts] = body.split('--');
        const rules = rulesPart.trim().split(/\s+/);
        const a11yRules = rules.filter((r) => r.startsWith('a11y-'));
        if (a11yRules.length === 0) continue;
        const justification = justificationParts.join('--').trim();
        if (a11yRules.length > 1 || rules.length !== a11yRules.length || !justification) {
          offenders.push(`${relative(SRC_ROOT, file)}: "${body}"`);
        }
      }
    }
    expect(offenders).toEqual([]);
  });
});
