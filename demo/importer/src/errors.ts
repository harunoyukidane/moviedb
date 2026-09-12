// Importer error classification (§13). These drive the retry and outcome logic.

/** Fatal: missing/invalid TMDB token (HTTP 401). Stop the whole run — no retry. */
export class InvalidTokenError extends Error {
  constructor(message = 'TMDB token is missing or invalid') {
    super(message);
    this.name = 'InvalidTokenError';
  }
}

/** The requested resource does not exist (HTTP 404). Record + continue others. */
export class NotFoundError extends Error {
  constructor(message = 'resource not found') {
    super(message);
    this.name = 'NotFoundError';
  }
}

/** A transient error (429/5xx/timeout) that was retried but never resolved. */
export class TransientError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'TransientError';
  }
}

/** A malformed/unexpected payload. Record + continue; never crash the run. */
export class MalformedResponseError extends Error {
  constructor(message = 'malformed response') {
    super(message);
    this.name = 'MalformedResponseError';
  }
}

/** Parse the committed manifest: one numeric id per line, `#` comments ignored. */
export function parseManifest(contents: string): number[] {
  const ids: number[] = [];
  for (const rawLine of contents.split(/\r?\n/)) {
    const line = rawLine.replace(/#.*$/, '').trim();
    if (line.length === 0) continue;
    const id = Number(line);
    if (!Number.isInteger(id) || id <= 0) continue;
    ids.push(id);
  }
  // de-duplicate while preserving order
  return [...new Set(ids)];
}
