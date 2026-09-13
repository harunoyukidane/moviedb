import type { PageServerLoad } from './$types';
import { error } from '@sveltejs/kit';
import { getMovie } from '$lib/server/operations';
import { GraphQlRequestError } from '$lib/server/graphql';
import { messageForCode } from '$lib/errors';

// The detail page is read-only. All mutations (delete movie, remove credits,
// artwork, add credit) live on the editor at /movies/[id]/edit.
export const load: PageServerLoad = async ({ params, request }) => {
  const correlationId = request.headers.get('x-correlation-id') ?? undefined;
  try {
    const movie = await getMovie(params.id, { correlationId });
    if (!movie) throw error(404, { message: messageForCode('NOT_FOUND') });
    return { movie };
  } catch (e) {
    if (e instanceof GraphQlRequestError) {
      if (e.code === 'NOT_FOUND') throw error(404, { message: messageForCode('NOT_FOUND') });
      throw error(503, { code: e.code, message: messageForCode(e.code) });
    }
    throw e;
  }
};
