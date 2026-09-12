import type { PageServerLoad, Actions } from './$types';
import { error, fail, redirect } from '@sveltejs/kit';
import { deleteMovie, getMovie, removeMovieCredit } from '$lib/server/operations';
import { GraphQlRequestError } from '$lib/server/graphql';
import { messageForCode } from '$lib/errors';

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

export const actions: Actions = {
  delete: async ({ params, request }) => {
    const correlationId = request.headers.get('x-correlation-id') ?? undefined;
    try {
      await deleteMovie(params.id, { correlationId });
    } catch (e) {
      const code = e instanceof GraphQlRequestError ? e.code : 'INTERNAL_ERROR';
      return fail(code === 'NOT_FOUND' ? 404 : 503, { message: messageForCode(code) });
    }
    throw redirect(303, '/movies');
  },

  removeCredit: async ({ request }) => {
    const form = await request.formData();
    const creditId = String(form.get('creditId') ?? '');
    const correlationId = request.headers.get('x-correlation-id') ?? undefined;
    try {
      await removeMovieCredit(creditId, { correlationId });
      return { removed: true };
    } catch (e) {
      const code = e instanceof GraphQlRequestError ? e.code : 'INTERNAL_ERROR';
      return fail(503, { message: messageForCode(code) });
    }
  }
};
