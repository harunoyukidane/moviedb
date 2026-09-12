// SERVER-ONLY media relay (BFF). The browser posts multipart to a SvelteKit
// action; the server relays the bytes to the Catalogue/People media endpoints and
// maps their `code` bodies to the same error contract. The browser never calls the
// media endpoints directly.
import { randomUUID } from 'node:crypto';
import { env } from '$env/dynamic/private';
import { CORRELATION_HEADER, GraphQlRequestError } from './graphql';

const catalogueBase = env.CATALOGUE_HTTP_URL ?? 'http://localhost:8080';
const peopleBase = env.PEOPLE_HTTP_URL ?? catalogueBase;

async function relay(method: string, url: string, file: File | null, correlationId: string): Promise<void> {
  const init: RequestInit = { method, headers: { [CORRELATION_HEADER]: correlationId } };
  if (file) {
    const body = new FormData();
    body.append('file', file, file.name || 'upload');
    init.body = body;
  }
  let res: Response;
  try {
    res = await fetch(url, init);
  } catch (e) {
    throw new GraphQlRequestError('DEPENDENCY_UNAVAILABLE', 'media service unreachable', correlationId);
  }
  if (!res.ok) {
    let code = 'INTERNAL_ERROR';
    try {
      const json = (await res.json()) as { code?: string; message?: string };
      code = json.code ?? code;
    } catch {
      if (res.status === 413) code = 'PAYLOAD_TOO_LARGE';
      else if (res.status === 415) code = 'UNSUPPORTED_MEDIA_TYPE';
      else if (res.status === 404) code = 'NOT_FOUND';
    }
    throw new GraphQlRequestError(code, `media request failed (${res.status})`, correlationId);
  }
}

export function uploadMovieArtwork(movieId: string, file: File, correlationId: string = randomUUID()) {
  return relay('PUT', `${catalogueBase}/api/movies/${movieId}/artwork`, file, correlationId);
}

export function deleteMovieArtwork(movieId: string, correlationId: string = randomUUID()) {
  return relay('DELETE', `${catalogueBase}/api/movies/${movieId}/artwork`, null, correlationId);
}

export function uploadPersonPhoto(personId: string, file: File, correlationId: string = randomUUID()) {
  return relay('PUT', `${peopleBase}/api/people/${personId}/photo`, file, correlationId);
}

export function deletePersonPhoto(personId: string, correlationId: string = randomUUID()) {
  return relay('DELETE', `${peopleBase}/api/people/${personId}/photo`, null, correlationId);
}
