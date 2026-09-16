import { render, screen } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { CreditRoleCode } from '$lib/server/types';

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
// reset is what silently blanked every field except genres — see the regression
// test below for why genres alone survived it.
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

const genreCodes = [
  { code: 'HORROR', title: 'Horror', description: '', active: true },
  { code: 'PSYCHOLOGICAL_HORROR', title: 'Psychological Horror', description: '', active: true }
];
const languages = [{ code: 'en', name: 'English', active: true }];
const roles: CreditRoleCode[] = [];

function makeMovie(overrides: Record<string, unknown> = {}) {
  return {
    id: 'movie-1',
    title: 'Original Title',
    originalTitle: 'Orig',
    synopsis: 'A gripping tale.',
    releaseDate: '2020-01-01',
    runtimeMinutes: 120,
    originalLanguage: 'en',
    language: { code: 'en', name: 'English', active: true },
    version: 0,
    artwork: null,
    genres: [{ code: 'HORROR', title: 'Horror', description: '', active: true }],
    cast: [],
    creators: [],
    ...overrides
  };
}

function expectAllFieldsPopulated() {
  expect(screen.getByLabelText('Title *')).toHaveValue('Original Title');
  expect(screen.getByLabelText('Original title')).toHaveValue('Orig');
  expect(screen.getByLabelText('Synopsis')).toHaveValue('A gripping tale.');
  expect(screen.getByLabelText('Release date')).toHaveValue('2020-01-01');
  expect(screen.getByLabelText('Runtime (min)')).toHaveValue(120);
  expect(screen.getByLabelText('Original language')).toHaveValue('English');
}

describe('movie edit form: saving keeps every field, not just the genre selection', () => {
  it('keeps title/originalTitle/synopsis/releaseDate/runtimeMinutes/originalLanguage/genres after a real save+reload', async () => {
    mockUpdate.mockClear();
    const user = userEvent.setup();
    const { rerender } = render(Page, {
      props: { data: { movie: makeMovie(), genres: genreCodes, roles, languages }, form: null }
    });
    expectAllFieldsPopulated();
    expect(screen.getByRole('checkbox', { name: 'Horror' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Psychological Horror' })).not.toBeChecked();

    // Select an additional genre, matching the reported bug scenario, then save.
    await user.click(screen.getByRole('checkbox', { name: 'Psychological Horror' }));
    await user.click(screen.getByRole('button', { name: /save changes/i }));

    // Simulate the reload SvelteKit's invalidateAll() performs after a
    // successful action: fresh data comes back from the server with the
    // same field values plus the newly saved genre.
    const reloaded = makeMovie({
      version: 1,
      genres: [
        { code: 'HORROR', title: 'Horror', description: '', active: true },
        { code: 'PSYCHOLOGICAL_HORROR', title: 'Psychological Horror', description: '', active: true }
      ]
    });
    await rerender({ data: { movie: reloaded, genres: genreCodes, roles, languages }, form: { updated: true } });

    expect(mockUpdate).toHaveBeenCalledWith(expect.any(HTMLFormElement), { reset: false });
    expectAllFieldsPopulated();
    expect(screen.getByRole('checkbox', { name: 'Horror' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Psychological Horror' })).toBeChecked();
  });
});
