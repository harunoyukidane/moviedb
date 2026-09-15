import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { describe, expect, it, vi } from 'vitest';
import { TmdbClient, type FetchFn } from './tmdb.js';
import { InvalidTokenError, NotFoundError, TransientError, MalformedResponseError, parseManifest } from './errors.js';
import { loadConfig } from './config.js';
import { GENRE_MAP } from './mappings.js';

const config = loadConfig({ TMDB_READ_TOKEN: 'test-token', IMPORT_MAX_RETRIES: '3', IMPORT_REQUEST_TIMEOUT_MS: '50' });

function jsonResponse(body: unknown, status = 200, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json', ...headers } });
}

const noSleep = async () => {};

describe('TmdbClient', () => {
  it('fetches a movie and sends the bearer token', async () => {
    const fetchFn: FetchFn = vi.fn(async (_url, init) => {
      expect((init?.headers as Record<string, string>).Authorization).toBe('Bearer test-token');
      return jsonResponse({ id: 694, title: 'The Shining' });
    });
    const client = new TmdbClient(config, fetchFn, noSleep);
    const movie = await client.getMovie(694);
    expect(movie.title).toBe('The Shining');
  });

  it('401 stops immediately with InvalidTokenError (no infinite retry)', async () => {
    const fetchFn: FetchFn = vi.fn(async () => jsonResponse({ status_message: 'invalid' }, 401));
    const client = new TmdbClient(config, fetchFn, noSleep);
    await expect(client.getMovie(1)).rejects.toBeInstanceOf(InvalidTokenError);
    expect(fetchFn).toHaveBeenCalledTimes(1);
  });

  it('404 throws NotFoundError without retrying', async () => {
    const fetchFn: FetchFn = vi.fn(async () => jsonResponse({}, 404));
    const client = new TmdbClient(config, fetchFn, noSleep);
    await expect(client.getMovie(999999)).rejects.toBeInstanceOf(NotFoundError);
    expect(fetchFn).toHaveBeenCalledTimes(1);
  });

  it('retries 429 then succeeds, honoring Retry-After', async () => {
    let calls = 0;
    const sleep = vi.fn(async () => {});
    const fetchFn: FetchFn = vi.fn(async () => {
      calls++;
      if (calls === 1) return jsonResponse({}, 429, { 'retry-after': '2' });
      return jsonResponse({ id: 5, title: 'OK' });
    });
    const client = new TmdbClient(config, fetchFn, sleep);
    const movie = await client.getMovie(5);
    expect(movie.title).toBe('OK');
    // Retry-After: 2s honored
    expect(sleep).toHaveBeenCalledWith(2000);
  });

  it('retries 5xx up to the cap then gives up with TransientError', async () => {
    const fetchFn: FetchFn = vi.fn(async () => jsonResponse({}, 503));
    const client = new TmdbClient(config, fetchFn, noSleep);
    await expect(client.getMovie(5)).rejects.toBeInstanceOf(TransientError);
    // initial + maxRetries(3) = 4 attempts
    expect(fetchFn).toHaveBeenCalledTimes(4);
  });

  it('a timeout (abort) is retried as transient and does not crash', async () => {
    const fetchFn: FetchFn = vi.fn(async () => {
      throw Object.assign(new Error('aborted'), { name: 'AbortError' });
    });
    const client = new TmdbClient(config, fetchFn, noSleep);
    await expect(client.getMovie(5)).rejects.toBeInstanceOf(TransientError);
    expect(fetchFn).toHaveBeenCalledTimes(4);
  });

  it('malformed (non-JSON) payload throws MalformedResponseError', async () => {
    const fetchFn: FetchFn = vi.fn(async () => new Response('<html>not json', { status: 200 }));
    const client = new TmdbClient(config, fetchFn, noSleep);
    await expect(client.getMovie(5)).rejects.toBeInstanceOf(MalformedResponseError);
  });

  it('missing required fields is malformed', async () => {
    const fetchFn: FetchFn = vi.fn(async () => jsonResponse({ id: 5 })); // no title
    const client = new TmdbClient(config, fetchFn, noSleep);
    await expect(client.getMovie(5)).rejects.toBeInstanceOf(MalformedResponseError);
  });

  it('getPoster returns null for a missing poster path and never throws', async () => {
    const client = new TmdbClient(config, vi.fn(), noSleep);
    expect(await client.getPoster(null)).toBeNull();
    expect(await client.getPoster(undefined)).toBeNull();
  });

  it('getPoster returns null (not throw) when the CDN fails', async () => {
    const fetchFn: FetchFn = vi.fn(async () => jsonResponse({}, 500));
    const client = new TmdbClient(config, fetchFn, noSleep);
    expect(await client.getPoster('/x.jpg')).toBeNull();
  });

  it('backoffMs honors Retry-After and otherwise grows with jitter', () => {
    const client = new TmdbClient(config, vi.fn(), noSleep, () => 0.5);
    expect(client.backoffMs(0, '3')).toBe(3000);
    // attempt 0 base 1000 + 12.5% jitter (rng 0.5 * 1000 * 0.25 = 125)
    expect(client.backoffMs(0, null)).toBe(1125);
  });
});

describe('parseManifest', () => {
  it('parses ids, ignoring comments/blanks and de-duplicating', () => {
    const ids = parseManifest('# header\n694  # The Shining\n\n539\n694\nnot-a-number\n');
    expect(ids).toEqual([694, 539]);
  });
});

describe('committed demo manifest (V2-07: genre/year filters demonstrable)', () => {
  const manifestPath = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', '..', 'tmdb-movie-ids.txt');
  const contents = readFileSync(manifestPath, 'utf8');
  const ids = parseManifest(contents);

  it('has no malformed/duplicate id lines silently dropped', () => {
    const nonCommentLines = contents
      .split(/\r?\n/)
      .map((l) => l.replace(/#.*$/, '').trim())
      .filter((l) => l.length > 0);
    expect(ids.length).toBe(nonCommentLines.length);
  });

  it('is broad enough to exercise every mapped genre and several release years', () => {
    // Broadened well past the original single-genre curated set (§12.1) so the
    // movie-listing genre/year filters (V2-05/V2-06) have demonstrable data.
    expect(ids.length).toBeGreaterThanOrEqual(24);
  });

  it('GENRE_MAP covers every non-editorial controlled genre (PSYCHOLOGICAL_HORROR is editorial-only)', () => {
    const mappedCodes = new Set(Object.values(GENRE_MAP));
    expect(mappedCodes).toEqual(
      new Set(['HORROR', 'ACTION', 'COMEDY', 'CRIME', 'DRAMA', 'MYSTERY', 'ROMANCE', 'THRILLER']),
    );
  });
});
