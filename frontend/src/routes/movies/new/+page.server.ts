import type { PageServerLoad, Actions } from './$types';
import { fail, redirect } from '@sveltejs/kit';
import { createMovie, listGenres, type CreateMovieInput } from '$lib/server/operations';
import { messageForCode, isValidationError } from '$lib/errors';
import { codeForError, requestContext } from '$lib/server/request';

export const load: PageServerLoad = async ({ request }) => {
  try {
    return { genres: await listGenres(requestContext(request)) };
  } catch {
    return { genres: [] };
  }
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

    const input: CreateMovieInput = {
      title,
      originalTitle: values.originalTitle || null,
      synopsis: values.synopsis,
      releaseDate: values.releaseDate || null,
      runtimeMinutes: values.runtimeMinutes ? Number(values.runtimeMinutes) : null,
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
      return fail(isValidationError(code) ? 400 : 503, { message: messageForCode(code), values });
    }
    throw redirect(303, `/movies/${id}`);
  }
};
