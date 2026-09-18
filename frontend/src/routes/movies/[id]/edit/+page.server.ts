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
import { messageForCode } from '$lib/errors';
import {
  codeForError,
  fieldErrorsForError,
  messageForError,
  requestContext,
  statusForCode,
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

    const values = {
      title: String(form.get('title') ?? '').trim(),
      originalTitle: String(form.get('originalTitle') ?? ''),
      synopsis: String(form.get('synopsis') ?? ''),
      releaseDate: String(form.get('releaseDate') ?? ''),
      runtimeMinutes: String(form.get('runtimeMinutes') ?? ''),
      originalLanguage: String(form.get('originalLanguage') ?? ''),
      genreCodes: form.getAll('genreCodes').map(String)
    };

    // Last-known-good values the form was loaded with (V2.2-11): only fields
    // that actually differ go into the update mask, so two edits touching
    // different fields don't collide on the row version (F21).
    const base = {
      title: String(form.get('base.title') ?? ''),
      originalTitle: String(form.get('base.originalTitle') ?? ''),
      synopsis: String(form.get('base.synopsis') ?? ''),
      releaseDate: String(form.get('base.releaseDate') ?? ''),
      runtimeMinutes: String(form.get('base.runtimeMinutes') ?? ''),
      originalLanguage: String(form.get('base.originalLanguage') ?? ''),
      genreCodes: form.getAll('base.genreCodes').map(String)
    };
    const sameSet = (a: string[], b: string[]) =>
      a.length === b.length && [...a].sort().every((v, i) => v === [...b].sort()[i]);
    const changed = {
      title: values.title !== base.title,
      originalTitle: values.originalTitle !== base.originalTitle,
      synopsis: values.synopsis !== base.synopsis,
      releaseDate: values.releaseDate !== base.releaseDate,
      runtimeMinutes: values.runtimeMinutes !== base.runtimeMinutes,
      originalLanguage: values.originalLanguage !== base.originalLanguage,
      genreCodes: !sameSet(values.genreCodes, base.genreCodes)
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
    if (changed.title) input.title = values.title;
    if (changed.originalTitle) input.originalTitle = values.originalTitle || null;
    if (changed.synopsis) input.synopsis = values.synopsis;
    if (changed.releaseDate) {
      const releaseDate = validateOptionalDate(values.releaseDate, 'releaseDate');
      if (isFieldError(releaseDate)) {
        return fail(400, {
          message: releaseDate.error.message,
          fieldErrors: { [releaseDate.error.field]: releaseDate.error.message },
          section: 'details',
          values
        });
      }
      input.releaseDate = releaseDate.value;
    }
    if (changed.runtimeMinutes) {
      const runtimeMinutes = validateOptionalInt(values.runtimeMinutes, 'runtimeMinutes');
      if (isFieldError(runtimeMinutes)) {
        return fail(400, {
          message: runtimeMinutes.error.message,
          fieldErrors: { [runtimeMinutes.error.field]: runtimeMinutes.error.message },
          section: 'details',
          values
        });
      }
      input.runtimeMinutes = runtimeMinutes.value;
    }
    if (changed.originalLanguage) input.originalLanguage = values.originalLanguage || null;
    if (changed.genreCodes) input.genreCodes = values.genreCodes;

    if (Object.keys(input).length === 0) {
      // Nothing actually changed - no mutation, no spurious conflict.
      return { updated: true };
    }

    try {
      await updateMovie(params.id, expectedVersion.value, input, context);
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

  uploadArtwork: async ({ params, request }) => {
    const correlationId = requestContext(request).correlationId;
    const form = await request.formData();
    const file = form.get('file');
    if (!(file instanceof File) || file.size === 0) {
      return fail(400, { message: 'Choose an image file first.', section: 'artwork' });
    }
    try {
      await uploadMovieArtwork(params.id, file, correlationId);
      return { artworkUploaded: true };
    } catch (e) {
      const code = codeForError(e);
      return fail(statusForCode(code), { message: messageForCode(code), section: 'artwork' });
    }
  },

  deleteArtwork: async ({ params, request }) => {
    const correlationId = requestContext(request).correlationId;
    try {
      await deleteMovieArtwork(params.id, correlationId);
      return { artworkDeleted: true };
    } catch (e) {
      const code = codeForError(e);
      return fail(statusForCode(code), { message: messageForCode(code), section: 'artwork' });
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
      return fail(statusForCode(code), {
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
      return fail(statusForCode(code), { message: messageForCode(code), section: 'credit' });
    }
  },

  delete: async ({ params, request }) => {
    const context = requestContext(request);
    try {
      await deleteMovie(params.id, context);
    } catch (e) {
      const code = codeForError(e);
      return fail(statusForCode(code), { message: messageForCode(code), section: 'danger' });
    }
    throw redirect(303, '/movies');
  }
};
