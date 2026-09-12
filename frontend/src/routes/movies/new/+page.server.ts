import type { PageServerLoad, Actions } from './$types';
import { fail, redirect } from '@sveltejs/kit';
import { createMovie, listGenres, type CreateMovieInput } from '$lib/server/operations';
import { GraphQlRequestError } from '$lib/server/graphql';
import { messageForCode, isValidationError } from '$lib/errors';

export const load: PageServerLoad = async ({ request }) => {
  const correlationId = request.headers.get('x-correlation-id') ?? undefined;
  try {
    return { genres: await listGenres({ correlationId }) };
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
      const created = await createMovie(input, {
        correlationId: request.headers.get('x-correlation-id') ?? undefined
      });
      id = created.id;
    } catch (e) {
      const code = e instanceof GraphQlRequestError ? e.code : 'INTERNAL_ERROR';
      // preserve the user's input so the form can re-render it
      return fail(isValidationError(code) ? 400 : 503, { message: messageForCode(code), values });
    }
    throw redirect(303, `/movies/${id}`);
  }
};
