import type { RequestHandler } from './$types';
import { env } from '$env/dynamic/private';

// BFF relay for person photo bytes: the browser's <img> hits this SvelteKit route,
// which fetches from the internal People media endpoint. Keeps People off the
// public network and avoids browser-to-backend calls.
const peopleBase = env.PEOPLE_HTTP_URL ?? env.CATALOGUE_HTTP_URL ?? 'http://localhost:8081';

export const GET: RequestHandler = async ({ params, request }) => {
  const upstream = `${peopleBase}/api/people/${params.personId}/photo`;
  const headers: Record<string, string> = {};
  const inm = request.headers.get('if-none-match');
  if (inm) headers['If-None-Match'] = inm;

  let res: Response;
  try {
    res = await fetch(upstream, { headers });
  } catch {
    return new Response(null, { status: 502 });
  }
  if (res.status === 304) return new Response(null, { status: 304, headers: passthroughHeaders(res) });
  if (!res.ok) return new Response(null, { status: res.status });

  return new Response(res.body, { status: 200, headers: passthroughHeaders(res) });
};

function passthroughHeaders(res: Response): Record<string, string> {
  const out: Record<string, string> = {};
  for (const h of ['content-type', 'content-length', 'etag', 'cache-control', 'x-content-type-options']) {
    const v = res.headers.get(h);
    if (v) out[h] = v;
  }
  return out;
}
