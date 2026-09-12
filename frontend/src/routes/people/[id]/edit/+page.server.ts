import type { PageServerLoad, Actions } from './$types';
import { error, fail } from '@sveltejs/kit';
import { getPerson, updatePerson } from '$lib/server/operations';
import { uploadPersonPhoto, deletePersonPhoto } from '$lib/server/media';
import { GraphQlRequestError } from '$lib/server/graphql';
import { messageForCode, isValidationError } from '$lib/errors';

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
  update: async ({ params, request }) => {
    const correlationId = request.headers.get('x-correlation-id') ?? undefined;
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
      await updatePerson(params.id, expectedVersion, input, { correlationId });
      return { updated: true };
    } catch (e) {
      const code = e instanceof GraphQlRequestError ? e.code : 'INTERNAL_ERROR';
      return fail(isValidationError(code) ? 400 : 409, { message: messageForCode(code), section: 'details' });
    }
  },

  uploadPhoto: async ({ params, request }) => {
    const correlationId = request.headers.get('x-correlation-id') ?? undefined;
    const form = await request.formData();
    const file = form.get('file');
    if (!(file instanceof File) || file.size === 0) {
      return fail(400, { message: messageForCode('BAD_USER_INPUT'), section: 'photo' });
    }
    try {
      await uploadPersonPhoto(params.id, file, correlationId);
      return { photoUploaded: true };
    } catch (e) {
      const code = e instanceof GraphQlRequestError ? e.code : 'INTERNAL_ERROR';
      return fail(code === 'PAYLOAD_TOO_LARGE' ? 413 : 415, { message: messageForCode(code), section: 'photo' });
    }
  },

  deletePhoto: async ({ params, request }) => {
    const correlationId = request.headers.get('x-correlation-id') ?? undefined;
    try {
      await deletePersonPhoto(params.id, correlationId);
      return { photoDeleted: true };
    } catch (e) {
      const code = e instanceof GraphQlRequestError ? e.code : 'INTERNAL_ERROR';
      return fail(503, { message: messageForCode(code), section: 'photo' });
    }
  }
};
