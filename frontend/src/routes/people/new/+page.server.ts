import type { PageServerLoad, Actions } from './$types';
import { fail, redirect } from '@sveltejs/kit';
import { createPerson, listCountries, type CreatePersonInput } from '$lib/server/operations';
import { codeForError, fieldErrorsForError, messageForError, requestContext, statusForCode } from '$lib/server/request';
import { isFieldError, validateOptionalDate } from '$lib/server/validation';

export const load: PageServerLoad = async ({ request }) => {
  const countries = await listCountries(requestContext(request)).catch(() => []);
  return { countries };
};

export const actions: Actions = {
  default: async ({ request }) => {
    const form = await request.formData();
    const values = {
      name: String(form.get('name') ?? '').trim(),
      biography: String(form.get('biography') ?? ''),
      birthDate: String(form.get('birthDate') ?? ''),
      deathDate: String(form.get('deathDate') ?? ''),
      placeOfBirth: String(form.get('placeOfBirth') ?? ''),
      birthCountryCode: String(form.get('birthCountryCode') ?? '')
    };

    const birthDate = validateOptionalDate(values.birthDate, 'birthDate');
    if (isFieldError(birthDate)) {
      return fail(400, {
        message: birthDate.error.message,
        fieldErrors: { [birthDate.error.field]: birthDate.error.message },
        values
      });
    }
    const deathDate = validateOptionalDate(values.deathDate, 'deathDate');
    if (isFieldError(deathDate)) {
      return fail(400, {
        message: deathDate.error.message,
        fieldErrors: { [deathDate.error.field]: deathDate.error.message },
        values
      });
    }

    const input: CreatePersonInput = {
      name: values.name,
      biography: values.biography,
      birthDate: birthDate.value,
      deathDate: deathDate.value,
      placeOfBirth: values.placeOfBirth || null,
      birthCountryCode: values.birthCountryCode || null
    };
    let id: string;
    try {
      const created = await createPerson(input, requestContext(request));
      id = created.id;
    } catch (e) {
      const code = codeForError(e);
      return fail(statusForCode(code), {
        message: messageForError(e, code),
        fieldErrors: fieldErrorsForError(e),
        values
      });
    }
    throw redirect(303, `/people/${id}`);
  }
};
