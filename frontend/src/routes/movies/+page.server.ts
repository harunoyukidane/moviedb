import type { PageServerLoad } from './$types';
import { listMovies } from '$lib/server/operations';
import { GraphQlRequestError } from '$lib/server/graphql';
import { messageForCode } from '$lib/errors';

const PAGE_SIZE = 20;

export const load: PageServerLoad = async ({ url, request }) => {
  const offset = Math.max(0, Number(url.searchParams.get('offset') ?? '0') || 0);
  const correlationId = request.headers.get('x-correlation-id') ?? undefined;
  try {
    const page = await listMovies(PAGE_SIZE, offset, { correlationId });
    return { page, error: null as string | null };
  } catch (e) {
    const code = e instanceof GraphQlRequestError ? e.code : 'INTERNAL_ERROR';
    // Degraded: render the screen with a dependency-error banner, not a hard crash.
    return {
      page: { items: [], total: 0, limit: PAGE_SIZE, offset },
      error: messageForCode(code)
    };
  }
};
