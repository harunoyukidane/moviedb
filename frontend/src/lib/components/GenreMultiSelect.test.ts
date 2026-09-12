import { render, screen } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import GenreMultiSelect from './GenreMultiSelect.svelte';
import type { GenreCode } from '$lib/server/types';

const genres: GenreCode[] = [
  { code: 'HORROR', title: 'Horror', description: '', active: true },
  { code: 'DRAMA', title: 'Drama', description: '', active: true }
];

describe('GenreMultiSelect', () => {
  it('renders a checkbox per genre and shows selected as tags with hidden inputs', async () => {
    const user = userEvent.setup();
    const { container } = render(GenreMultiSelect, { props: { genres, selected: [] } });

    // no hidden inputs when nothing selected
    expect(container.querySelectorAll('input[type="hidden"][name="genreCodes"]').length).toBe(0);

    await user.click(screen.getByRole('checkbox', { name: 'Horror' }));

    // a tag appears and a hidden input carries the code to the form action
    const hidden = container.querySelectorAll('input[type="hidden"][name="genreCodes"]');
    expect(hidden.length).toBe(1);
    expect((hidden[0] as HTMLInputElement).value).toBe('HORROR');
  });

  it('removes a selected genre via its tag remove button', async () => {
    const user = userEvent.setup();
    const { container } = render(GenreMultiSelect, { props: { genres, selected: ['HORROR'] } });
    expect(container.querySelectorAll('input[type="hidden"][name="genreCodes"]').length).toBe(1);

    await user.click(screen.getByRole('button', { name: 'Remove Horror' }));
    expect(container.querySelectorAll('input[type="hidden"][name="genreCodes"]').length).toBe(0);
  });
});
