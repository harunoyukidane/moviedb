import { render, screen } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

const mockUpdate = vi.fn(async (form: HTMLFormElement, opts?: { reset?: boolean; invalidateAll?: boolean }) => {
  if (opts?.reset !== false) {
    HTMLFormElement.prototype.reset.call(form);
  }
});

// Real SvelteKit `enhance` needs a running app router (invalidateAll/applyAction
// reach into client internals that aren't initialized in a component test). This
// fake reproduces just the one behavior the bug hinges on: SvelteKit's `update()`
// fallback resets the native <form> element unless explicitly told `reset: false`
// (see @sveltejs/kit's `fallback_callback` in runtime/app/forms.js). That native
// reset is what silently blanked name/biography/dates/placeOfBirth after saving
// just the country - the field the user reported ("select a country in person
// and save and the whole profile got wiped").
vi.mock('$app/forms', () => ({
  enhance: (form: HTMLFormElement, submit: any) => {
    const handler = async (event: Event) => {
      event.preventDefault();
      let cancelled = false;
      const callback =
        (await submit({
          action: new URL(form.getAttribute('action') ?? '', window.location.href),
          cancel: () => {
            cancelled = true;
          },
          controller: new AbortController(),
          formData: new FormData(form),
          formElement: form,
          submitter: null
        })) ?? null;
      if (cancelled || !callback) return;

      const result = { type: 'success' as const, status: 200, data: {} };
      const update = (opts?: { reset?: boolean; invalidateAll?: boolean }) => mockUpdate(form, opts);
      await callback({
        action: new URL(form.getAttribute('action') ?? '', window.location.href),
        formData: new FormData(form),
        formElement: form,
        update,
        result
      });
    };
    form.addEventListener('submit', handler);
    return { destroy: () => form.removeEventListener('submit', handler) };
  }
}));

import Page from './+page.svelte';

const countries = [
  { code: 'US', name: 'United States', active: true },
  { code: 'FR', name: 'France', active: true }
];

function makePerson(overrides: Record<string, unknown> = {}) {
  return {
    id: 'person-1',
    name: 'Adam Driver',
    biography: 'Legendary American actor.',
    birthDate: '1983-11-19',
    deathDate: null,
    placeOfBirth: 'San Diego',
    birthCountryCode: null,
    version: 0,
    credits: [],
    ...overrides
  };
}

function expectAllFieldsPopulated() {
  expect(screen.getByLabelText('Name *')).toHaveValue('Adam Driver');
  expect(screen.getByLabelText('Biography')).toHaveValue('Legendary American actor.');
  expect(screen.getByLabelText('Birth date')).toHaveValue('1983-11-19');
  expect(screen.getByLabelText('Place of birth')).toHaveValue('San Diego');
}

describe('person edit form: saving keeps every field, not just the country selection', () => {
  it('resets the native form when update runs without reset: false', async () => {
    mockUpdate.mockClear();
    const form = document.createElement('form');
    const name = document.createElement('input');
    name.name = 'name';
    name.defaultValue = ''; // matches value={v.name} - Svelte sets the property, not the attribute
    name.value = 'Adam Driver';
    form.append(name);

    await mockUpdate(form);

    expect(mockUpdate).toHaveBeenCalledOnce();
    expect(mockUpdate).toHaveBeenCalledWith(form);
    expect(name.value).toBe('');
  });

  it('keeps name/biography/birthDate/placeOfBirth after selecting a country and saving (regression)', async () => {
    mockUpdate.mockClear();
    const user = userEvent.setup();
    render(Page, {
      props: {
        data: { person: makePerson(), countries, photoUrl: '/api/people/person-1/photo' },
        form: null
      }
    });
    expectAllFieldsPopulated();

    // Select a country - the exact reported scenario - then save.
    await user.type(screen.getByLabelText('Country'), 'United States');
    await user.click(await screen.findByRole('button', { name: 'United States' }));
    await user.click(screen.getByRole('button', { name: /save changes/i }));

    expect(mockUpdate).toHaveBeenCalledWith(expect.any(HTMLFormElement), { reset: false });
    // The bug: without reset:false, the native form.reset() blanks every
    // uncontrolled field (name, biography, birthDate, placeOfBirth) even
    // though only birthCountryCode was actually submitted as changed.
    expectAllFieldsPopulated();
  });
});
