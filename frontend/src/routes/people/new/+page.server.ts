import type { Actions } from './$types';
import { fail, redirect } from '@sveltejs/kit';
import { createPerson, type CreatePersonInput } from '$lib/server/operations';
import { GraphQlRequestError } from '$lib/server/graphql';
import { messageForCode, isValidationError } from '$lib/errors';

export const actions: Actions = {
  default: async ({ request }) => {
    const form = await request.formData();
    const values = {
      name: String(form.get('name') ?? '').trim(),
      biography: String(form.get('biography') ?? ''),
      birthDate: String(form.get('birthDate') ?? ''),
      deathDate: String(form.get('deathDate') ?? ''),
      placeOfBirth: String(form.get('placeOfBirth') ?? '')
    };
    const input: CreatePersonInput = {
      name: values.name,
      biography: values.biography,
      birthDate: values.birthDate || null,
      deathDate: values.deathDate || null,
      placeOfBirth: values.placeOfBirth || null
    };
    let id: string;
    try {
      const created = await createPerson(input, {
        correlationId: request.headers.get('x-correlation-id') ?? undefined
      });
      id = created.id;
    } catch (e) {
      const code = e instanceof GraphQlRequestError ? e.code : 'INTERNAL_ERROR';
      return fail(isValidationError(code) ? 400 : 503, { message: messageForCode(code), values });
    }
    throw redirect(303, `/people/${id}`);
  }
};
