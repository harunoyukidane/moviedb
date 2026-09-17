import type { PageServerLoad, Actions } from './$types';
import { fail, redirect } from '@sveltejs/kit';
import { createMovie, listGenres, listLanguages, type CreateMovieInput } from '$lib/server/operations';
import { isValidationError } from '$lib/errors';
import { codeForError, fieldErrorsForError, messageForError, requestContext } from '$lib/server/request';
import { isFieldError, validateOptionalDate, validateOptionalInt } from '$lib/server/validation';

export const load: PageServerLoad = async ({ request }) => {
  const context = requestContext(request);
  const [genres, languages] = await Promise.all([
    listGenres(context).catch(() => []),
    listLanguages(context).catch(() => [])
  ]);
  return { genres, languages };
};

export const actions: Actions = {
  default: async ({ request }) => {
    const form = await request.formData();
    const title = String(form.get('title') ?? '').trim();
    const values = {
      title,
      originalTitle: String(form.get('originalTitle') ?? ''),
      synopsis: String(form.get('synopsis') ?? ''),
      releaseDate: String(form.get('releaseDate') ?? ''),
      runtimeMinutes: String(form.get('runtimeMinutes') ?? ''),
      originalLanguage: String(form.get('originalLanguage') ?? ''),
      genreCodes: form.getAll('genreCodes').map(String)
    };

    const releaseDate = validateOptionalDate(values.releaseDate, 'releaseDate');
    if (isFieldError(releaseDate)) {
      return fail(400, {
        message: releaseDate.error.message,
        fieldErrors: { [releaseDate.error.field]: releaseDate.error.message },
        values
      });
    }
    const runtimeMinutes = validateOptionalInt(values.runtimeMinutes, 'runtimeMinutes');
    if (isFieldError(runtimeMinutes)) {
      return fail(400, {
        message: runtimeMinutes.error.message,
        fieldErrors: { [runtimeMinutes.error.field]: runtimeMinutes.error.message },
        values
      });
    }

    const input: CreateMovieInput = {
      title,
      originalTitle: values.originalTitle || null,
      synopsis: values.synopsis,
      releaseDate: releaseDate.value,
      runtimeMinutes: runtimeMinutes.value,
      originalLanguage: values.originalLanguage || null,
      genreCodes: values.genreCodes
    };

    let id: string;
    try {
      const created = await createMovie(input, requestContext(request));
      id = created.id;
    } catch (e) {
      const code = codeForError(e);
      // preserve the user's input so the form can re-render it
      return fail(isValidationError(code) ? 400 : 503, {
        message: messageForError(e, code),
        fieldErrors: fieldErrorsForError(e),
        values
      });
    }
    throw redirect(303, `/movies/${id}`);
  }
};
