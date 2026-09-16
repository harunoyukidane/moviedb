import { render, screen } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import DateField from './DateField.svelte';

describe('DateField', () => {
  it('renders a date input with the given id, name, and value', () => {
    render(DateField, { props: { id: 'releaseDate', name: 'releaseDate', value: '1999-03-31' } });
    const input = document.getElementById('releaseDate') as HTMLInputElement;
    expect(input).toHaveAttribute('type', 'date');
    expect(input).toHaveAttribute('name', 'releaseDate');
    expect(input.value).toBe('1999-03-31');
  });

  it('renders an empty value when none is given', () => {
    render(DateField, { props: { id: 'birthDate', name: 'birthDate' } });
    const input = document.getElementById('birthDate') as HTMLInputElement;
    expect(input.value).toBe('');
  });

  it('exposes an accessible "Open calendar" button', () => {
    render(DateField, { props: { id: 'releaseDate', name: 'releaseDate' } });
    expect(screen.getByRole('button', { name: 'Open calendar' })).toBeInTheDocument();
  });

  it('calls the input\'s native showPicker() when clicked', async () => {
    const user = userEvent.setup();
    render(DateField, { props: { id: 'releaseDate', name: 'releaseDate' } });
    const input = document.getElementById('releaseDate') as HTMLInputElement;
    const showPicker = vi.fn();
    // jsdom doesn't implement showPicker; stub it to simulate a real browser.
    (input as unknown as { showPicker: () => void }).showPicker = showPicker;

    await user.click(screen.getByRole('button', { name: 'Open calendar' }));
    expect(showPicker).toHaveBeenCalledOnce();
  });

  it('falls back to focusing the input when showPicker is unsupported', async () => {
    const user = userEvent.setup();
    render(DateField, { props: { id: 'releaseDate', name: 'releaseDate' } });
    const input = document.getElementById('releaseDate') as HTMLInputElement;
    expect(input.showPicker).toBeUndefined();

    await user.click(screen.getByRole('button', { name: 'Open calendar' }));
    expect(input).toHaveFocus();
  });
});
