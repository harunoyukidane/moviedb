import type { PageServerLoad, Actions } from './$types';
import { error, fail, redirect } from '@sveltejs/kit';
import { deletePerson, getPerson, listCountries, updatePerson } from '$lib/server/operations';
import { uploadPersonPhoto, deletePersonPhoto } from '$lib/server/media';
import { messageForCode } from '$lib/errors';
import {
  codeForError,
  fieldErrorsForError,
  messageForError,
  requestContext,
  statusForCode,
  throwPageLoadError
} from '$lib/server/request';
import { isFieldError, validateOptionalDate, validateVersion } from '$lib/server/validation';

export const load: PageServerLoad = async ({ params, request }) => {
  const context = requestContext(request);
  try {
    const [person, countries] = await Promise.all([
      getPerson(params.id, context),
      listCountries(context).catch(() => [])
    ]);
    if (!person) throw error(404, { message: messageForCode('NOT_FOUND') });
    return { person, countries, photoUrl: `/api/people/${params.id}/photo` };
  } catch (e) {
    throwPageLoadError(e);
  }
};

export const actions: Actions = {
  update: async ({ params, request }) => {
    const context = requestContext(request);
    const form = await request.formData();

    const values = {
      name: String(form.get('name') ?? '').trim(),
      biography: String(form.get('biography') ?? ''),
      birthDate: String(form.get('birthDate') ?? ''),
      deathDate: String(form.get('deathDate') ?? ''),
      placeOfBirth: String(form.get('placeOfBirth') ?? ''),
      birthCountryCode: String(form.get('birthCountryCode') ?? '')
    };

    // Last-known-good values the form was loaded with (V2.2-11): only fields
    // that actually differ go into the update mask, so two edits touching
    // different fields don't collide on the row version (F21).
    const base = {
      name: String(form.get('base.name') ?? ''),
      biography: String(form.get('base.biography') ?? ''),
      birthDate: String(form.get('base.birthDate') ?? ''),
      deathDate: String(form.get('base.deathDate') ?? ''),
      placeOfBirth: String(form.get('base.placeOfBirth') ?? ''),
      birthCountryCode: String(form.get('base.birthCountryCode') ?? '')
    };
    const changed = {
      name: values.name !== base.name,
      biography: values.biography !== base.biography,
      birthDate: values.birthDate !== base.birthDate,
      deathDate: values.deathDate !== base.deathDate,
      placeOfBirth: values.placeOfBirth !== base.placeOfBirth,
      birthCountryCode: values.birthCountryCode !== base.birthCountryCode
    };

    const expectedVersion = validateVersion(String(form.get('expectedVersion') ?? ''));
    if (isFieldError(expectedVersion)) {
      return fail(400, {
        message: expectedVersion.error.message,
        fieldErrors: undefined,
        section: 'details',
        values
      });
    }

    const input: Record<string, unknown> = {};
    if (changed.name) input.name = values.name;
    if (changed.biography) input.biography = values.biography;
    if (changed.birthDate) {
      const birthDate = validateOptionalDate(values.birthDate, 'birthDate');
      if (isFieldError(birthDate)) {
        return fail(400, {
          message: birthDate.error.message,
          fieldErrors: { [birthDate.error.field]: birthDate.error.message },
          section: 'details',
          values
        });
      }
      input.birthDate = birthDate.value;
    }
    if (changed.deathDate) {
      const deathDate = validateOptionalDate(values.deathDate, 'deathDate');
      if (isFieldError(deathDate)) {
        return fail(400, {
          message: deathDate.error.message,
          fieldErrors: { [deathDate.error.field]: deathDate.error.message },
          section: 'details',
          values
        });
      }
      input.deathDate = deathDate.value;
    }
    if (changed.placeOfBirth) input.placeOfBirth = values.placeOfBirth || null;
    if (changed.birthCountryCode) input.birthCountryCode = values.birthCountryCode || null;

    if (Object.keys(input).length === 0) {
      // Nothing actually changed - no mutation, no spurious conflict.
      return { updated: true };
    }

    try {
      await updatePerson(params.id, expectedVersion.value, input, context);
      return { updated: true };
    } catch (e) {
      const code = codeForError(e);
      return fail(statusForCode(code), {
        message: messageForError(e, code),
        fieldErrors: fieldErrorsForError(e),
        section: 'details',
        values
      });
    }
  },

  uploadPhoto: async ({ params, request }) => {
    const correlationId = requestContext(request).correlationId;
    const form = await request.formData();
    const file = form.get('file');
    if (!(file instanceof File) || file.size === 0) {
      return fail(400, { message: 'Choose an image file first.', section: 'photo' });
    }
    try {
      await uploadPersonPhoto(params.id, file, correlationId);
      return { photoUploaded: true };
    } catch (e) {
      const code = codeForError(e);
      return fail(statusForCode(code), { message: messageForCode(code), section: 'photo' });
    }
  },

  deletePhoto: async ({ params, request }) => {
    const correlationId = requestContext(request).correlationId;
    try {
      await deletePersonPhoto(params.id, correlationId);
      return { photoDeleted: true };
    } catch (e) {
      const code = codeForError(e);
      return fail(statusForCode(code), { message: messageForCode(code), section: 'photo' });
    }
  },

  delete: async ({ params, request }) => {
    const context = requestContext(request);
    try {
      await deletePerson(params.id, context);
    } catch (e) {
      const code = codeForError(e);
      return fail(statusForCode(code), { message: messageForCode(code), code, section: 'danger' });
    }
    throw redirect(303, '/people');
  }
};
