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

  it('passes min and max through to the native input', () => {
    render(DateField, {
      props: { id: 'releaseDate', name: 'releaseDate', min: '1888-10-14', max: '2036-01-01' }
    });
    const input = document.getElementById('releaseDate') as HTMLInputElement;
    expect(input).toHaveAttribute('min', '1888-10-14');
    expect(input).toHaveAttribute('max', '2036-01-01');
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

describe('DateField error association (V2.8-03)', () => {
  // Before this, a rejected date rendered its message below the field but left
  // the input unmarked: the `input[aria-invalid='true']` border rule never
  // matched, so nothing was visibly highlighted (despite the banner copy saying
  // "see the highlighted field"), and a screen reader on the field announced no
  // error. The plain inputs on the same forms had both wired up all along.
  it('points at its error message so a screen reader announces it on the field', () => {
    render(DateField, { props: { id: 'birthDate', name: 'birthDate' } });
    // The message is rendered alongside by <FieldError id="birthDate-error">.
    expect(document.getElementById('birthDate')).toHaveAttribute(
      'aria-describedby',
      'birthDate-error'
    );
  });

  it('is not marked invalid by default', () => {
    render(DateField, { props: { id: 'deathDate', name: 'deathDate' } });
    expect(document.getElementById('deathDate')).toHaveAttribute('aria-invalid', 'false');
  });

  it('marks the control invalid, which is what the red border rule keys on', () => {
    render(DateField, { props: { id: 'deathDate', name: 'deathDate', invalid: true } });
    expect(document.getElementById('deathDate')).toHaveAttribute('aria-invalid', 'true');
  });
});
