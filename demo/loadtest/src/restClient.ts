// Artwork/photo upload endpoints are plain HTTP multipart (never GraphQL, see
// docs/architecture/interfaces.md). We POST/PUT with a tiny synthetic PNG so
// concurrent uploads to the same movie/person exercise the same storage-swap
// path the real UI does, without needing real image assets.

// A 1x1 transparent PNG, the smallest payload that still passes the server's
// "decode to validate it's really an image" check.
const TINY_PNG_BASE64 =
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=';

export interface HttpOpError {
  status: number;
  code: string | undefined;
  message: string;
}

export class RestOpError extends Error {
  constructor(readonly status: number, readonly code: string | undefined, message: string) {
    super(message);
    this.name = 'RestOpError';
  }
}

async function classify(res: Response): Promise<RestOpError> {
  try {
    const body = (await res.json()) as { code?: string; message?: string };
    return new RestOpError(res.status, body.code, body.message ?? res.statusText);
  } catch {
    return new RestOpError(res.status, undefined, res.statusText || `HTTP ${res.status}`);
  }
}

function tinyPngBlob(): Blob {
  const bytes = Buffer.from(TINY_PNG_BASE64, 'base64');
  return new Blob([bytes], { type: 'image/png' });
}

export async function uploadMovieArtwork(catalogueHttpUrl: string, movieId: string): Promise<void> {
  const form = new FormData();
  form.append('file', tinyPngBlob(), 'loadtest.png');
  const res = await fetch(`${catalogueHttpUrl}/api/movies/${movieId}/artwork`, {
    method: 'PUT',
    body: form
  });
  if (!res.ok) throw await classify(res);
}

export async function uploadPersonPhoto(peopleHttpUrl: string, personId: string): Promise<void> {
  const form = new FormData();
  form.append('file', tinyPngBlob(), 'loadtest.png');
  const res = await fetch(`${peopleHttpUrl}/api/people/${personId}/photo`, {
    method: 'PUT',
    body: form
  });
  if (!res.ok) throw await classify(res);
}
