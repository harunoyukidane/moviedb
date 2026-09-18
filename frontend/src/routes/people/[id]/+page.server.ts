import type { PageServerLoad, Actions } from './$types';
import { error, fail } from '@sveltejs/kit';
import { getPerson } from '$lib/server/operations';
import { deletePersonPhoto } from '$lib/server/media';
import { messageForCode } from '$lib/errors';
import { codeForError, requestContext, statusForCode, throwPageLoadError } from '$lib/server/request';

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
  deletePhoto: async ({ params, request }) => {
    const correlationId = requestContext(request).correlationId;
    try {
      await deletePersonPhoto(params.id, correlationId);
      return { photoDeleted: true };
    } catch (e) {
      const code = codeForError(e);
      return fail(statusForCode(code), { message: messageForCode(code) });
    }
  }
};
