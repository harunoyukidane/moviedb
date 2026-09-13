import type { PageServerLoad } from './$types';
import { error } from '@sveltejs/kit';
import { getMovie } from '$lib/server/operations';
import { messageForCode } from '$lib/errors';
import { requestContext, throwPageLoadError } from '$lib/server/request';

// The detail page is read-only. All mutations (delete movie, remove credits,
// artwork, add credit) live on the editor at /movies/[id]/edit.
export const load: PageServerLoad = async ({ params, request }) => {
  try {
    const movie = await getMovie(params.id, requestContext(request));
    if (!movie) throw error(404, { message: messageForCode('NOT_FOUND') });
    return { movie };
  } catch (e) {
    throwPageLoadError(e);
  }
};
