import { render, screen } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import CodeCombobox from './CodeCombobox.svelte';

// Same case list LanguageSelect.test.ts exercises against the LanguageSelect
// wrapper, run here against the shared primitive directly with two different
// item sets (languages, countries) - proving the generalization kept the
// exact behavior LanguageSelect already had (V2.2-10).
describe.each([
  {
    kind: 'languages',
    label: 'Language',
    items: [
      { code: 'en', name: 'English' },
      { code: 'fr', name: 'French' },
      { code: 'ja', name: 'Japanese' }
    ]
  },
  {
    kind: 'countries',
    label: 'Country',
    items: [
      { code: 'US', name: 'United States' },
      { code: 'FR', name: 'France' },
      { code: 'JP', name: 'Japan' }
    ]
  }
])('CodeCombobox ($kind)', ({ label, items }) => {
  function hiddenValue(name = 'code'): string {
    return (document.querySelector(`input[type="hidden"][name="${name}"]`) as HTMLInputElement).value;
  }

  const [a, b, c] = items;

  it('prefills the display name for an initially selected code', () => {
    render(CodeCombobox, { props: { items, selected: b.code, name: 'code', id: 'code', label } });
    expect(screen.getByLabelText(label)).toHaveValue(b.name);
    expect(hiddenValue()).toBe(b.code);
  });

  it('starts empty with no selection', () => {
    render(CodeCombobox, { props: { items, selected: null, name: 'code', id: 'code', label } });
    expect(screen.getByLabelText(label)).toHaveValue('');
    expect(hiddenValue()).toBe('');
  });

  it('filters suggestions by name as the user types', async () => {
    const user = userEvent.setup();
    render(CodeCombobox, { props: { items, name: 'code', id: 'code', label } });
    await user.type(screen.getByLabelText(label), c.name.slice(0, 3));
    expect(await screen.findByRole('button', { name: c.name })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: b.name })).not.toBeInTheDocument();
  });

  it('filters suggestions by code as well as name', async () => {
    const user = userEvent.setup();
    render(CodeCombobox, { props: { items, name: 'code', id: 'code', label } });
    await user.type(screen.getByLabelText(label), b.code);
    expect(await screen.findByRole('button', { name: b.name })).toBeInTheDocument();
  });

  it('picking a suggestion sets the hidden code input and closes the list', async () => {
    const user = userEvent.setup();
    render(CodeCombobox, { props: { items, name: 'code', id: 'code', label } });
    await user.type(screen.getByLabelText(label), a.name.slice(0, 3));
    await user.click(await screen.findByRole('button', { name: a.name }));

    expect(screen.getByLabelText(label)).toHaveValue(a.name);
    expect(hiddenValue()).toBe(a.code);
    expect(screen.queryByRole('listbox')).not.toBeInTheDocument();
  });

  it('typing over a previous selection clears the hidden code until a new pick is made', async () => {
    const user = userEvent.setup();
    render(CodeCombobox, { props: { items, selected: a.code, name: 'code', id: 'code', label } });
    const input = screen.getByLabelText(label);
    await user.clear(input);
    await user.type(input, 'xyz');
    expect(hiddenValue()).toBe('');
  });

  it('clears unmatched typed text on blur so no fake selection lingers', async () => {
    const user = userEvent.setup();
    render(CodeCombobox, { props: { items, name: 'code', id: 'code', label } });
    const input = screen.getByLabelText(label);
    await user.type(input, 'nonexistent');
    await user.tab();
    expect(input).toHaveValue('');
  });
});
