// Tracks outcomes per operation so the final report can separate "the app
// correctly rejected a race" (CONFLICT/NOT_FOUND/etc — expected under
// concurrent writers) from "something actually broke" (5xx, timeouts, thrown
// exceptions, unrecognised errors).

const EXPECTED_CODES = new Set([
  'CONFLICT',
  'NOT_FOUND',
  'BAD_USER_INPUT',
  'PERSON_IN_USE',
  'PAYLOAD_TOO_LARGE',
  'UNSUPPORTED_MEDIA_TYPE',
  'DEPENDENCY_UNAVAILABLE'
]);

interface OpStats {
  attempts: number;
  success: number;
  expectedErrors: Map<string, number>;
  unexpectedErrors: Map<string, number>;
  unexpectedSamples: string[];
  latenciesMs: number[];
}

function emptyOpStats(): OpStats {
  return {
    attempts: 0,
    success: 0,
    expectedErrors: new Map(),
    unexpectedErrors: new Map(),
    unexpectedSamples: [],
    latenciesMs: []
  };
}

const MAX_SAMPLES = 10;

export class Stats {
  private readonly ops = new Map<string, OpStats>();
  private readonly startedAt = Date.now();

  private opFor(op: string): OpStats {
    let s = this.ops.get(op);
    if (!s) {
      s = emptyOpStats();
      this.ops.set(op, s);
    }
    return s;
  }

  recordSuccess(op: string, latencyMs: number): void {
    const s = this.opFor(op);
    s.attempts += 1;
    s.success += 1;
    s.latenciesMs.push(latencyMs);
  }

  /** code=undefined means the failure wasn't a recognised, stable error code. */
  recordFailure(op: string, latencyMs: number, code: string | undefined, detail: string): void {
    const s = this.opFor(op);
    s.attempts += 1;
    s.latenciesMs.push(latencyMs);
    if (code && EXPECTED_CODES.has(code)) {
      s.expectedErrors.set(code, (s.expectedErrors.get(code) ?? 0) + 1);
      return;
    }
    const key = code ?? 'UNRECOGNISED';
    s.unexpectedErrors.set(key, (s.unexpectedErrors.get(key) ?? 0) + 1);
    if (s.unexpectedSamples.length < MAX_SAMPLES) {
      s.unexpectedSamples.push(detail);
    }
  }

  hasUnexpectedErrors(): boolean {
    for (const s of this.ops.values()) {
      if (s.unexpectedErrors.size > 0) return true;
    }
    return false;
  }

  private percentile(sorted: number[], p: number): number {
    if (sorted.length === 0) return 0;
    const idx = Math.min(sorted.length - 1, Math.floor(p * sorted.length));
    return sorted[idx];
  }

  print(): void {
    const elapsedSec = (Date.now() - this.startedAt) / 1000;
    console.log('');
    console.log('='.repeat(78));
    console.log(' LOAD TEST REPORT');
    console.log('='.repeat(78));

    let totalAttempts = 0;
    let totalSuccess = 0;

    for (const [op, s] of [...this.ops.entries()].sort((a, b) => a[0].localeCompare(b[0]))) {
      totalAttempts += s.attempts;
      totalSuccess += s.success;
      const sorted = [...s.latenciesMs].sort((a, b) => a - b);
      const p50 = this.percentile(sorted, 0.5);
      const p95 = this.percentile(sorted, 0.95);
      const p99 = this.percentile(sorted, 0.99);
      const max = sorted.length ? sorted[sorted.length - 1] : 0;

      console.log('');
      console.log(`${op}`);
      console.log(
        `  attempts=${s.attempts} success=${s.success} ` +
          `throughput=${(s.attempts / elapsedSec).toFixed(1)}/s`
      );
      console.log(`  latency ms: p50=${p50} p95=${p95} p99=${p99} max=${max}`);
      if (s.expectedErrors.size > 0) {
        const parts = [...s.expectedErrors.entries()].map(([code, n]) => `${code}=${n}`);
        console.log(`  expected errors (correct behaviour under contention): ${parts.join(', ')}`);
      }
      if (s.unexpectedErrors.size > 0) {
        const parts = [...s.unexpectedErrors.entries()].map(([code, n]) => `${code}=${n}`);
        console.log(`  ** UNEXPECTED ERRORS **: ${parts.join(', ')}`);
        for (const sample of s.unexpectedSamples) {
          console.log(`     - ${sample}`);
        }
      }
    }

    console.log('');
    console.log('-'.repeat(78));
    console.log(
      `TOTAL: ${totalAttempts} attempts, ${totalSuccess} succeeded, ` +
        `${(totalAttempts / elapsedSec).toFixed(1)} ops/sec over ${elapsedSec.toFixed(1)}s`
    );
    console.log(
      this.hasUnexpectedErrors()
        ? 'RESULT: unexpected errors were seen — see ** UNEXPECTED ERRORS ** above.'
        : 'RESULT: no unexpected errors. All failures were correctly-handled races (expected error codes).'
    );
    console.log('='.repeat(78));
  }
}
