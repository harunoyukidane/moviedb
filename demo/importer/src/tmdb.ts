import type { ImporterConfig } from './config.js';
import { InvalidTokenError, MalformedResponseError, NotFoundError, TransientError } from './errors.js';

// Minimal shapes of the TMDB payloads we consume.
export interface TmdbCastMember {
  id: number;
  name: string;
  character?: string;
  order?: number;
  profile_path?: string | null;
  credit_id: string;
  birthday?: string | null;
  deathday?: string | null;
  place_of_birth?: string | null;
}

export interface TmdbCrewMember {
  id: number;
  name: string;
  job: string;
  profile_path?: string | null;
  credit_id: string;
}

export interface TmdbMovie {
  id: number;
  title: string;
  original_title?: string;
  overview?: string;
  release_date?: string;
  runtime?: number | null;
  original_language?: string;
  poster_path?: string | null;
  genres?: { id: number; name: string }[];
  credits?: { cast?: TmdbCastMember[]; crew?: TmdbCrewMember[] };
}

export interface TmdbPersonDetails {
  id: number;
  name: string;
  biography?: string | null;
  birthday?: string | null;
  deathday?: string | null;
  place_of_birth?: string | null;
}

export type FetchFn = (url: string, init?: RequestInit) => Promise<Response>;
export type SleepFn = (ms: number) => Promise<void>;

const defaultSleep: SleepFn = (ms) => new Promise((r) => setTimeout(r, ms));

/**
 * TMDB client with bounded exponential backoff + jitter, honoring `Retry-After`
 * on 429/5xx, request deadlines, and deliberate error classification (§13).
 */
export class TmdbClient {
  constructor(
    private readonly config: ImporterConfig,
    private readonly fetchFn: FetchFn = fetch,
    private readonly sleep: SleepFn = defaultSleep,
    private readonly rng: () => number = Math.random
  ) {}

  /** Fetch a movie with appended credits. Classifies failures per §13. */
  async getMovie(id: number): Promise<TmdbMovie> {
    const url = `${this.config.tmdbApiBase}/3/movie/${id}?append_to_response=credits`;
    const res = await this.requestWithRetry(url);
    let json: unknown;
    try {
      json = await res.json();
    } catch {
      throw new MalformedResponseError(`movie ${id}: response was not JSON`);
    }
    const movie = json as TmdbMovie;
    if (typeof movie?.id !== 'number' || typeof movie?.title !== 'string') {
      throw new MalformedResponseError(`movie ${id}: missing required fields`);
    }
    return movie;
  }

  /**
   * Fetch person details (biography/dates/place of birth). Best-effort from the
   * caller's perspective: throws the same classified errors as getMovie, so a
   * missing/failed person fetch is on the caller to catch and fall back on -
   * it must never fail the whole movie import.
   */
  async getPerson(id: number): Promise<TmdbPersonDetails> {
    const url = `${this.config.tmdbApiBase}/3/person/${id}`;
    const res = await this.requestWithRetry(url);
    let json: unknown;
    try {
      json = await res.json();
    } catch {
      throw new MalformedResponseError(`person ${id}: response was not JSON`);
    }
    const person = json as TmdbPersonDetails;
    if (typeof person?.id !== 'number' || typeof person?.name !== 'string') {
      throw new MalformedResponseError(`person ${id}: missing required fields`);
    }
    return person;
  }

  /** Download poster bytes; returns null if there is no poster or it can't be fetched. */
  async getPoster(posterPath: string | null | undefined): Promise<{ bytes: Uint8Array; contentType: string } | null> {
    return this.getImage(posterPath, this.config.tmdbPosterSize);
  }

  /** Download a person profile image; null if absent or unfetchable (non-fatal). */
  async getProfileImage(profilePath: string | null | undefined): Promise<{ bytes: Uint8Array; contentType: string } | null> {
    return this.getImage(profilePath, this.config.tmdbProfileSize);
  }

  /** Fetch an image from the TMDB CDN at the given size; null on any failure. */
  private async getImage(path: string | null | undefined, size: string): Promise<{ bytes: Uint8Array; contentType: string } | null> {
    if (!path) return null;
    const url = `${this.config.tmdbImageBase}${size}${path}`;
    try {
      const res = await this.requestWithRetry(url, false);
      const buf = new Uint8Array(await res.arrayBuffer());
      const contentType = res.headers.get('content-type') ?? 'image/jpeg';
      return { bytes: buf, contentType };
    } catch {
      // A missing/failed image must not fail the import (§13).
      return null;
    }
  }

  /**
   * Perform a GET with retries. `authorized` adds the bearer token (TMDB API);
   * image CDN requests are unauthenticated.
   */
  private async requestWithRetry(url: string, authorized = true): Promise<Response> {
    let attempt = 0;
    let lastError: Error | null = null;
    while (attempt <= this.config.maxRetries) {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), this.config.requestTimeoutMs);
      try {
        const headers: Record<string, string> = { Accept: 'application/json' };
        if (authorized) headers.Authorization = `Bearer ${this.config.tmdbReadToken}`;
        const res = await this.fetchFn(url, { headers, signal: controller.signal });
        clearTimeout(timer);

        if (res.status === 401) throw new InvalidTokenError();
        if (res.status === 404) throw new NotFoundError(`404 for ${url}`);
        if (res.status === 429 || res.status >= 500) {
          lastError = new TransientError(`HTTP ${res.status} for ${url}`);
          if (attempt === this.config.maxRetries) break;
          await this.sleep(this.backoffMs(attempt, res.headers.get('retry-after')));
          attempt++;
          continue;
        }
        if (!res.ok) throw new TransientError(`HTTP ${res.status} for ${url}`);
        return res;
      } catch (e) {
        clearTimeout(timer);
        // Fatal + terminal classifications propagate immediately.
        if (e instanceof InvalidTokenError || e instanceof NotFoundError) throw e;
        // Abort (timeout) and network errors are transient -> retry.
        lastError = e instanceof Error ? e : new TransientError(String(e));
        if (attempt === this.config.maxRetries) break;
        await this.sleep(this.backoffMs(attempt, null));
        attempt++;
      }
    }
    throw new TransientError(`exhausted retries for ${url}: ${lastError?.message ?? 'unknown'}`);
  }

  /** Exponential backoff with jitter; honors an integer/second `Retry-After`. */
  backoffMs(attempt: number, retryAfter: string | null): number {
    if (retryAfter) {
      const seconds = Number(retryAfter);
      if (Number.isFinite(seconds) && seconds >= 0) return Math.round(seconds * 1000);
    }
    const base = Math.min(1000 * 2 ** attempt, 30_000);
    const jitter = this.rng() * base * 0.25; // up to 25% jitter
    return Math.round(base + jitter);
  }
}
