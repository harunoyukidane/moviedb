import { render, screen } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import LanguageSelect from './LanguageSelect.svelte';
import type { LanguageCode } from '$lib/server/types';

const languages: LanguageCode[] = [
  { code: 'en', name: 'English', active: true },
  { code: 'fr', name: 'French', active: true },
  { code: 'ja', name: 'Japanese', active: true }
];

function hiddenValue(name = 'originalLanguage'): string {
  return (document.querySelector(`input[type="hidden"][name="${name}"]`) as HTMLInputElement).value;
}

describe('LanguageSelect', () => {
  it('prefills the display name for an initially selected code', () => {
    render(LanguageSelect, { props: { languages, selected: 'fr' } });
    expect(screen.getByLabelText('Original language')).toHaveValue('French');
    expect(hiddenValue()).toBe('fr');
  });

  it('starts empty with no selection', () => {
    render(LanguageSelect, { props: { languages, selected: null } });
    expect(screen.getByLabelText('Original language')).toHaveValue('');
    expect(hiddenValue()).toBe('');
  });

  it('filters suggestions by name as the user types', async () => {
    const user = userEvent.setup();
    render(LanguageSelect, { props: { languages } });
    await user.type(screen.getByLabelText('Original language'), 'jap');
    expect(await screen.findByRole('option', { name: 'Japanese' })).toBeInTheDocument();
    expect(screen.queryByRole('option', { name: 'French' })).not.toBeInTheDocument();
  });

  it('filters suggestions by code as well as name', async () => {
    const user = userEvent.setup();
    render(LanguageSelect, { props: { languages } });
    await user.type(screen.getByLabelText('Original language'), 'fr');
    expect(await screen.findByRole('option', { name: 'French' })).toBeInTheDocument();
  });

  it('picking a suggestion with the mouse sets the hidden code input and closes the list', async () => {
    const user = userEvent.setup();
    render(LanguageSelect, { props: { languages } });
    await user.type(screen.getByLabelText('Original language'), 'eng');
    await user.click(await screen.findByRole('option', { name: 'English' }));

    expect(screen.getByLabelText('Original language')).toHaveValue('English');
    expect(hiddenValue()).toBe('en');
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
  });

  it('picking a suggestion with the keyboard sets the hidden code input and closes the list', async () => {
    const user = userEvent.setup();
    render(LanguageSelect, { props: { languages } });
    const input = screen.getByLabelText('Original language');
    await user.type(input, 'eng');
    await user.keyboard('{ArrowDown}{Enter}');

    expect(input).toHaveValue('English');
    expect(hiddenValue()).toBe('en');
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
  });

  it('typing over a previous selection clears the hidden code until a new pick is made', async () => {
    const user = userEvent.setup();
    render(LanguageSelect, { props: { languages, selected: 'en' } });
    const input = screen.getByLabelText('Original language');
    await user.clear(input);
    await user.type(input, 'xyz');
    expect(hiddenValue()).toBe('');
  });

  it('clears unmatched typed text on blur so no fake selection lingers', async () => {
    const user = userEvent.setup();
    render(LanguageSelect, { props: { languages } });
    const input = screen.getByLabelText('Original language');
    await user.type(input, 'notalanguage');
    await user.tab();
    expect(input).toHaveValue('');
  });
});
