import type { RequestHandler } from './$types';
import { json } from '@sveltejs/kit';
import { listPeople } from '$lib/server/operations';
import { GraphQlRequestError } from '$lib/server/graphql';

// BFF autocomplete endpoint: the browser calls THIS SvelteKit route, which calls
// GraphQL server-side. No browser-to-GraphQL traffic.
export const GET: RequestHandler = async ({ url, request }) => {
  const query = url.searchParams.get('q')?.trim() ?? '';
  if (query.length < 1) return json({ items: [] });
  const correlationId = request.headers.get('x-correlation-id') ?? undefined;
  try {
    const page = await listPeople(query, 8, 0, { correlationId });
    return json({ items: page.items.map((p) => ({ id: p.id, name: p.name })) });
  } catch (e) {
    const code = e instanceof GraphQlRequestError ? e.code : 'INTERNAL_ERROR';
    return json({ items: [], error: code }, { status: 200 });
  }
};
