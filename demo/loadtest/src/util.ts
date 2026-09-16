import type { Stats } from './stats.js';
import { GraphqlOpError } from './graphqlClient.js';
import { RestOpError } from './restClient.js';

export function randomInt(maxExclusive: number): number {
  return Math.floor(Math.random() * maxExclusive);
}

export function pick<T>(items: readonly T[]): T {
  return items[randomInt(items.length)];
}

let counter = 0;
/** Process-unique-ish tag for synthetic records so they're easy to spot/clean up. */
export function nextTag(): string {
  counter += 1;
  return `${Date.now().toString(36)}-${counter}`;
}

/**
 * Runs one timed attempt of a load-test operation and records the outcome.
 * Never throws — a worker loop can call this in a tight while-loop without
 * its own try/catch.
 */
export async function attempt<T>(stats: Stats, op: string, fn: () => Promise<T>): Promise<T | undefined> {
  const start = Date.now();
  try {
    const result = await fn();
    stats.recordSuccess(op, Date.now() - start);
    return result;
  } catch (err) {
    const latency = Date.now() - start;
    if (err instanceof GraphqlOpError) {
      stats.recordFailure(op, latency, err.code, err.message);
    } else if (err instanceof RestOpError) {
      stats.recordFailure(op, latency, err.code ?? `HTTP_${err.status}`, err.message);
    } else {
      const message = err instanceof Error ? err.message : String(err);
      stats.recordFailure(op, latency, undefined, message);
    }
    return undefined;
  }
}
