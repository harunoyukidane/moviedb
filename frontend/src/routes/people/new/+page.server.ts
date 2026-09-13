import type { Actions } from './$types';
import { fail, redirect } from '@sveltejs/kit';
import { createPerson, type CreatePersonInput } from '$lib/server/operations';
import { messageForCode, isValidationError } from '$lib/errors';
import { codeForError, requestContext } from '$lib/server/request';

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
      const created = await createPerson(input, requestContext(request));
      id = created.id;
    } catch (e) {
      const code = codeForError(e);
      return fail(isValidationError(code) ? 400 : 503, { message: messageForCode(code), values });
    }
    throw redirect(303, `/people/${id}`);
  }
};
