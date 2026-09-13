import type { PageServerLoad, Actions } from './$types';
import { error, fail, redirect } from '@sveltejs/kit';
import { deletePerson, getPerson } from '$lib/server/operations';
import { deletePersonPhoto } from '$lib/server/media';
import { messageForCode } from '$lib/errors';
import { codeForError, requestContext, throwPageLoadError } from '$lib/server/request';

export const load: PageServerLoad = async ({ params, request }) => {
  try {
    const person = await getPerson(params.id, requestContext(request));
    if (!person) throw error(404, { message: messageForCode('NOT_FOUND') });
    return { person, photoUrl: `/api/people/${params.id}/photo` };
  } catch (e) {
    throwPageLoadError(e);
  }
};

export const actions: Actions = {
  delete: async ({ params, request }) => {
    const context = requestContext(request);
    try {
      await deletePerson(params.id, context);
    } catch (e) {
      const code = codeForError(e);
      // PERSON_IN_USE surfaces clearly (409) so the UI can explain the fix.
      return fail(code === 'PERSON_IN_USE' ? 409 : 503, { message: messageForCode(code), code });
    }
    throw redirect(303, '/people');
  },

  deletePhoto: async ({ params, request }) => {
    const correlationId = requestContext(request).correlationId;
    try {
      await deletePersonPhoto(params.id, correlationId);
      return { photoDeleted: true };
    } catch (e) {
      const code = codeForError(e);
      return fail(503, { message: messageForCode(code) });
    }
  }
};
