import type { PageServerLoad, Actions } from './$types';
import { error, fail } from '@sveltejs/kit';
import { getPerson, updatePerson } from '$lib/server/operations';
import { uploadPersonPhoto, deletePersonPhoto } from '$lib/server/media';
import { messageForCode, isValidationError } from '$lib/errors';
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
  update: async ({ params, request }) => {
    const context = requestContext(request);
    const form = await request.formData();
    const expectedVersion = Number(form.get('expectedVersion') ?? '0');
    const input: Record<string, unknown> = {
      name: String(form.get('name') ?? '').trim(),
      biography: String(form.get('biography') ?? ''),
      birthDate: String(form.get('birthDate') ?? '') || null,
      deathDate: String(form.get('deathDate') ?? '') || null,
      placeOfBirth: String(form.get('placeOfBirth') ?? '') || null
    };
    try {
      await updatePerson(params.id, expectedVersion, input, context);
      return { updated: true };
    } catch (e) {
      const code = codeForError(e);
      return fail(isValidationError(code) ? 400 : 409, { message: messageForCode(code), section: 'details' });
    }
  },

  uploadPhoto: async ({ params, request }) => {
    const correlationId = requestContext(request).correlationId;
    const form = await request.formData();
    const file = form.get('file');
    if (!(file instanceof File) || file.size === 0) {
      return fail(400, { message: messageForCode('BAD_USER_INPUT'), section: 'photo' });
    }
    try {
      await uploadPersonPhoto(params.id, file, correlationId);
      return { photoUploaded: true };
    } catch (e) {
      const code = codeForError(e);
      return fail(code === 'PAYLOAD_TOO_LARGE' ? 413 : 415, { message: messageForCode(code), section: 'photo' });
    }
  },

  deletePhoto: async ({ params, request }) => {
    const correlationId = requestContext(request).correlationId;
    try {
      await deletePersonPhoto(params.id, correlationId);
      return { photoDeleted: true };
    } catch (e) {
      const code = codeForError(e);
      return fail(503, { message: messageForCode(code), section: 'photo' });
    }
  }
};
