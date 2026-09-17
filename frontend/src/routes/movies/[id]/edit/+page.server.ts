import type { PageServerLoad, Actions } from './$types';
import { error, fail, redirect } from '@sveltejs/kit';
import {
  addMovieCredit,
  deleteMovie,
  getMovie,
  listCreditRoles,
  listGenres,
  listLanguages,
  removeMovieCredit,
  updateMovie,
  type CreateCreditInput
} from '$lib/server/operations';
import { uploadMovieArtwork, deleteMovieArtwork } from '$lib/server/media';
import { messageForCode, isValidationError } from '$lib/errors';
import {
  codeForError,
  fieldErrorsForError,
  messageForError,
  requestContext,
  throwPageLoadError
} from '$lib/server/request';
import { isFieldError, validateOptionalDate, validateOptionalInt, validateVersion } from '$lib/server/validation';

export const load: PageServerLoad = async ({ params, request }) => {
  const context = requestContext(request);
  try {
    const [movie, genres, roles, languages] = await Promise.all([
      getMovie(params.id, context),
      listGenres(context),
      listCreditRoles(context),
      listLanguages(context)
    ]);
    if (!movie) throw error(404, { message: messageForCode('NOT_FOUND') });
    return { movie, genres, roles, languages };
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
    const releaseDate = validateOptionalDate(String(form.get('releaseDate') ?? ''), 'releaseDate');
    if (isFieldError(releaseDate)) {
      return fail(400, {
        message: releaseDate.error.message,
        fieldErrors: { [releaseDate.error.field]: releaseDate.error.message },
        section: 'details'
      });
    }
    const runtimeMinutes = validateOptionalInt(String(form.get('runtimeMinutes') ?? ''), 'runtimeMinutes');
    if (isFieldError(runtimeMinutes)) {
      return fail(400, {
        message: runtimeMinutes.error.message,
        fieldErrors: { [runtimeMinutes.error.field]: runtimeMinutes.error.message },
        section: 'details'
      });
    }

    const input: Record<string, unknown> = {
      title: String(form.get('title') ?? '').trim(),
      originalTitle: (String(form.get('originalTitle') ?? '') || null),
      synopsis: String(form.get('synopsis') ?? ''),
      releaseDate: releaseDate.value,
      runtimeMinutes: runtimeMinutes.value,
      originalLanguage: String(form.get('originalLanguage') ?? '') || null,
      genreCodes: form.getAll('genreCodes').map(String)
    };
    try {
      await updateMovie(params.id, expectedVersion.value, input, context);
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

  uploadArtwork: async ({ params, request }) => {
    const correlationId = requestContext(request).correlationId;
    const form = await request.formData();
    const file = form.get('file');
    if (!(file instanceof File) || file.size === 0) {
      return fail(400, { message: messageForCode('BAD_USER_INPUT'), section: 'artwork' });
    }
    try {
      await uploadMovieArtwork(params.id, file, correlationId);
      return { artworkUploaded: true };
    } catch (e) {
      const code = codeForError(e);
      return fail(code === 'PAYLOAD_TOO_LARGE' ? 413 : 415, { message: messageForCode(code), section: 'artwork' });
    }
  },

  deleteArtwork: async ({ params, request }) => {
    const correlationId = requestContext(request).correlationId;
    try {
      await deleteMovieArtwork(params.id, correlationId);
      return { artworkDeleted: true };
    } catch (e) {
      const code = codeForError(e);
      return fail(503, { message: messageForCode(code), section: 'artwork' });
    }
  },

  addCredit: async ({ params, request }) => {
    const context = requestContext(request);
    const form = await request.formData();
    const input: CreateCreditInput = {
      personId: String(form.get('personId') ?? '').trim(),
      roleCode: String(form.get('roleCode') ?? '').trim(),
      characterName: (String(form.get('characterName') ?? '') || null),
      billingOrder: form.get('billingOrder') ? Number(form.get('billingOrder')) : null
    };
    try {
      await addMovieCredit(params.id, input, context);
      return { creditAdded: true };
    } catch (e) {
      const code = codeForError(e);
      return fail(isValidationError(code) ? 400 : 409, {
        message: messageForError(e, code),
        fieldErrors: fieldErrorsForError(e),
        section: 'credit'
      });
    }
  },

  removeCredit: async ({ request }) => {
    const context = requestContext(request);
    const form = await request.formData();
    const creditId = String(form.get('creditId') ?? '');
    try {
      await removeMovieCredit(creditId, context);
      return { creditRemoved: true };
    } catch (e) {
      const code = codeForError(e);
      return fail(503, { message: messageForCode(code), section: 'credit' });
    }
  },

  delete: async ({ params, request }) => {
    const context = requestContext(request);
    try {
      await deleteMovie(params.id, context);
    } catch (e) {
      const code = codeForError(e);
      return fail(code === 'NOT_FOUND' ? 404 : 503, { message: messageForCode(code), section: 'danger' });
    }
    throw redirect(303, '/movies');
  }
};
