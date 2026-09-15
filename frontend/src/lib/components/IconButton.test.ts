import { render, screen } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import IconButton from './IconButton.svelte';

describe('IconButton', () => {
  it('is a real button with an accessible name from the required label prop', () => {
    render(IconButton, { props: { icon: 'delete', label: 'Remove Jane Star from movie' } });
    const btn = screen.getByRole('button', { name: 'Remove Jane Star from movie' });
    expect(btn.tagName).toBe('BUTTON');
    expect(btn).toHaveAttribute('title', 'Remove Jane Star from movie');
  });

  it('defaults to type="button" so it never accidentally submits a form', () => {
    render(IconButton, { props: { icon: 'delete', label: 'Remove' } });
    expect(screen.getByRole('button')).toHaveAttribute('type', 'button');
  });

  it('accepts type="submit" for use inside a per-row removal form', () => {
    render(IconButton, { props: { icon: 'delete', label: 'Remove', type: 'submit' } });
    expect(screen.getByRole('button')).toHaveAttribute('type', 'submit');
  });

  it('forwards click events to the caller', async () => {
    const user = userEvent.setup();
    const handler = vi.fn();
    const { component } = render(IconButton, { props: { icon: 'delete', label: 'Remove' } });
    component.$on('click', handler);
    await user.click(screen.getByRole('button', { name: 'Remove' }));
    expect(handler).toHaveBeenCalledOnce();
  });

  it('renders the danger variant distinctly', () => {
    render(IconButton, { props: { icon: 'delete', label: 'Remove', variant: 'danger' } });
    expect(screen.getByRole('button')).toHaveClass('danger');
  });
});
