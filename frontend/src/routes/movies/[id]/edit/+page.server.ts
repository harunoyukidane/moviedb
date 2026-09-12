import type { PageServerLoad, Actions } from './$types';
import { error, fail } from '@sveltejs/kit';
import {
  addMovieCredit,
  getMovie,
  listCreditRoles,
  listGenres,
  updateMovie,
  type CreateCreditInput
} from '$lib/server/operations';
import { uploadMovieArtwork, deleteMovieArtwork } from '$lib/server/media';
import { GraphQlRequestError } from '$lib/server/graphql';
import { messageForCode, isValidationError } from '$lib/errors';

export const load: PageServerLoad = async ({ params, request }) => {
  const correlationId = request.headers.get('x-correlation-id') ?? undefined;
  try {
    const [movie, genres, roles] = await Promise.all([
      getMovie(params.id, { correlationId }),
      listGenres({ correlationId }),
      listCreditRoles({ correlationId })
    ]);
    if (!movie) throw error(404, { message: messageForCode('NOT_FOUND') });
    return { movie, genres, roles };
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
      title: String(form.get('title') ?? '').trim(),
      originalTitle: (String(form.get('originalTitle') ?? '') || null),
      synopsis: String(form.get('synopsis') ?? ''),
      releaseDate: String(form.get('releaseDate') ?? '') || null,
      runtimeMinutes: form.get('runtimeMinutes') ? Number(form.get('runtimeMinutes')) : null,
      originalLanguage: String(form.get('originalLanguage') ?? '') || null,
      genreCodes: form.getAll('genreCodes').map(String)
    };
    try {
      await updateMovie(params.id, expectedVersion, input, { correlationId });
      return { updated: true };
    } catch (e) {
      const code = e instanceof GraphQlRequestError ? e.code : 'INTERNAL_ERROR';
      return fail(isValidationError(code) ? 400 : 409, { message: messageForCode(code), section: 'details' });
    }
  },

  uploadArtwork: async ({ params, request }) => {
    const correlationId = request.headers.get('x-correlation-id') ?? undefined;
    const form = await request.formData();
    const file = form.get('file');
    if (!(file instanceof File) || file.size === 0) {
      return fail(400, { message: messageForCode('BAD_USER_INPUT'), section: 'artwork' });
    }
    try {
      await uploadMovieArtwork(params.id, file, correlationId);
      return { artworkUploaded: true };
    } catch (e) {
      const code = e instanceof GraphQlRequestError ? e.code : 'INTERNAL_ERROR';
      return fail(code === 'PAYLOAD_TOO_LARGE' ? 413 : 415, { message: messageForCode(code), section: 'artwork' });
    }
  },

  deleteArtwork: async ({ params, request }) => {
    const correlationId = request.headers.get('x-correlation-id') ?? undefined;
    try {
      await deleteMovieArtwork(params.id, correlationId);
      return { artworkDeleted: true };
    } catch (e) {
      const code = e instanceof GraphQlRequestError ? e.code : 'INTERNAL_ERROR';
      return fail(503, { message: messageForCode(code), section: 'artwork' });
    }
  },

  addCredit: async ({ params, request }) => {
    const correlationId = request.headers.get('x-correlation-id') ?? undefined;
    const form = await request.formData();
    const input: CreateCreditInput = {
      personId: String(form.get('personId') ?? '').trim(),
      roleCode: String(form.get('roleCode') ?? '').trim(),
      characterName: (String(form.get('characterName') ?? '') || null),
      billingOrder: form.get('billingOrder') ? Number(form.get('billingOrder')) : null
    };
    try {
      await addMovieCredit(params.id, input, { correlationId });
      return { creditAdded: true };
    } catch (e) {
      const code = e instanceof GraphQlRequestError ? e.code : 'INTERNAL_ERROR';
      return fail(isValidationError(code) ? 400 : 409, { message: messageForCode(code), section: 'credit' });
    }
  }
};
