import type { RequestHandler } from './$types';
import { json } from '@sveltejs/kit';
import { search } from '$lib/server/operations';
import { GraphQlRequestError } from '$lib/server/graphql';
import { messageForCode } from '$lib/errors';

// BFF search endpoint: the browser calls this SvelteKit route, which runs the
// unified GraphQL search server-side. Blank queries return empty (the box simply
// clears) rather than surfacing a validation error mid-typing.
export const GET: RequestHandler = async ({ url, request }) => {
  const query = url.searchParams.get('q')?.trim() ?? '';
  if (query.length === 0) return json({ movies: [], people: [] });
  const correlationId = request.headers.get('x-correlation-id') ?? undefined;
  try {
    const result = await search(query, 10, 0, { correlationId });
    return json(result);
  } catch (e) {
    const code = e instanceof GraphQlRequestError ? e.code : 'INTERNAL_ERROR';
    return json({ movies: [], people: [], error: messageForCode(code) });
  }
};
