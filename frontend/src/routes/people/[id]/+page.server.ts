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
    // Versioned so the browser's 30-day photo cache (PersonPhotoController) is
    // busted whenever the person changes - including a photo re-upload, which
    // bumps @Version - instead of showing the old photo until the cache expires.
    return { person, photoUrl: `/api/people/${params.id}/photo?v=${person.version}` };
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
