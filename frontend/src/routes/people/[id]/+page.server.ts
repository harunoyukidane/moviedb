import type { PageServerLoad, Actions } from './$types';
import { error, fail, redirect } from '@sveltejs/kit';
import { deletePerson, getPerson } from '$lib/server/operations';
import { deletePersonPhoto } from '$lib/server/media';
import { GraphQlRequestError } from '$lib/server/graphql';
import { messageForCode } from '$lib/errors';

export const load: PageServerLoad = async ({ params, request }) => {
  const correlationId = request.headers.get('x-correlation-id') ?? undefined;
  try {
    const person = await getPerson(params.id, { correlationId });
    if (!person) throw error(404, { message: messageForCode('NOT_FOUND') });
    return { person, photoUrl: `/api/people/${params.id}/photo` };
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
      await deletePerson(params.id, { correlationId });
    } catch (e) {
      const code = e instanceof GraphQlRequestError ? e.code : 'INTERNAL_ERROR';
      // PERSON_IN_USE surfaces clearly (409) so the UI can explain the fix.
      return fail(code === 'PERSON_IN_USE' ? 409 : 503, { message: messageForCode(code), code });
    }
    throw redirect(303, '/people');
  },

  deletePhoto: async ({ params, request }) => {
    const correlationId = request.headers.get('x-correlation-id') ?? undefined;
    try {
      await deletePersonPhoto(params.id, correlationId);
      return { photoDeleted: true };
    } catch (e) {
      const code = e instanceof GraphQlRequestError ? e.code : 'INTERNAL_ERROR';
      return fail(503, { message: messageForCode(code) });
    }
  }
};
