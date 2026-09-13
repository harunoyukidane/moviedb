import type { RequestHandler } from './$types';
import { env } from '$env/dynamic/private';
import { proxyMediaGet } from '$lib/server/media-proxy';

// BFF relay for person photo bytes: the browser's <img> hits this SvelteKit route,
// which fetches from the internal People media endpoint. Keeps People off the
// public network and avoids browser-to-backend calls.
const peopleBase = env.PEOPLE_HTTP_URL ?? env.CATALOGUE_HTTP_URL ?? 'http://localhost:8081';

export const GET: RequestHandler = async ({ params, request }) => {
  return proxyMediaGet(`${peopleBase}/api/people/${params.personId}/photo`, request);
};
