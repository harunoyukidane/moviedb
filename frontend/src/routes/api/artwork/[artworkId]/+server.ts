import type { RequestHandler } from './$types';
import { env } from '$env/dynamic/private';
import { proxyMediaGet } from '$lib/server/media-proxy';

// BFF relay for movie artwork bytes: the browser's <img src="/api/artwork/{id}">
// hits this SvelteKit route, which fetches from the internal Catalogue media
// endpoint. Keeps the Catalogue's HTTP surface off the public network.
const catalogueBase = env.CATALOGUE_HTTP_URL ?? 'http://localhost:8080';

export const GET: RequestHandler = async ({ params, request }) => {
  return proxyMediaGet(`${catalogueBase}/api/artwork/${params.artworkId}`, request);
};
