import type { PageServerLoad, Actions } from './$types';
import { error, fail, redirect } from '@sveltejs/kit';
import { deletePerson, getPerson, updatePerson } from '$lib/server/operations';
import { uploadPersonPhoto, deletePersonPhoto } from '$lib/server/media';
import { messageForCode, isValidationError } from '$lib/errors';
import {
  codeForError,
  fieldErrorsForError,
  messageForError,
  requestContext,
  throwPageLoadError
} from '$lib/server/request';
import { isFieldError, validateOptionalDate, validateVersion } from '$lib/server/validation';

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

    const expectedVersion = validateVersion(String(form.get('expectedVersion') ?? ''));
    if (isFieldError(expectedVersion)) {
      return fail(400, { message: expectedVersion.error.message, fieldErrors: undefined, section: 'details' });
    }
    const birthDate = validateOptionalDate(String(form.get('birthDate') ?? ''), 'birthDate');
    if (isFieldError(birthDate)) {
      return fail(400, {
        message: birthDate.error.message,
        fieldErrors: { [birthDate.error.field]: birthDate.error.message },
        section: 'details'
      });
    }
    const deathDate = validateOptionalDate(String(form.get('deathDate') ?? ''), 'deathDate');
    if (isFieldError(deathDate)) {
      return fail(400, {
        message: deathDate.error.message,
        fieldErrors: { [deathDate.error.field]: deathDate.error.message },
        section: 'details'
      });
    }

    const input: Record<string, unknown> = {
      name: String(form.get('name') ?? '').trim(),
      biography: String(form.get('biography') ?? ''),
      birthDate: birthDate.value,
      deathDate: deathDate.value,
      placeOfBirth: String(form.get('placeOfBirth') ?? '') || null
    };
    try {
      await updatePerson(params.id, expectedVersion.value, input, context);
      return { updated: true };
    } catch (e) {
      const code = codeForError(e);
      return fail(isValidationError(code) ? 400 : 409, {
        message: messageForError(e, code),
        fieldErrors: fieldErrorsForError(e),
        section: 'details'
      });
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
  },

  delete: async ({ params, request }) => {
    const context = requestContext(request);
    try {
      await deletePerson(params.id, context);
    } catch (e) {
      const code = codeForError(e);
      // PERSON_IN_USE surfaces clearly (409) so the UI can explain the fix.
      return fail(code === 'PERSON_IN_USE' ? 409 : 503, { message: messageForCode(code), code, section: 'danger' });
    }
    throw redirect(303, '/people');
  }
};
