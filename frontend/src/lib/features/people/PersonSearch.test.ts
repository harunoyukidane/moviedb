import { fireEvent, render, screen, waitFor } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import PersonSearch from './PersonSearch.svelte';

type Hit = { id: string; name: string };

describe('PersonSearch', () => {
  it('debounces rapid keystrokes into a single suggestion request', async () => {
    const user = userEvent.setup();
    const suggestFn = vi.fn(async (_q: string): Promise<Hit[]> => []);
    render(PersonSearch, { props: { suggestFn, debounceMs: 50 } });

    await user.type(screen.getByRole('combobox'), 'pacino');
    await new Promise((r) => setTimeout(r, 120));

    expect(suggestFn).toHaveBeenCalledTimes(1);
    expect(suggestFn.mock.calls[0]?.[0]).toBe('pacino');
  });

  it('shows suggestions and navigates on selection instead of submitting the form', async () => {
    const user = userEvent.setup();
    const suggestFn = vi.fn(async (): Promise<Hit[]> => [{ id: 'p1', name: 'Al Pacino' }]);
    const navigateFn = vi.fn();
    render(PersonSearch, { props: { suggestFn, navigateFn, debounceMs: 10 } });

    await user.type(screen.getByRole('combobox'), 'pacino');
    await waitFor(() => expect(screen.getByRole('option', { name: 'Al Pacino' })).toBeInTheDocument());

    await user.click(screen.getByRole('button', { name: 'Al Pacino' }));
    expect(navigateFn).toHaveBeenCalledWith('/people/p1');
    expect(screen.queryByRole('option', { name: 'Al Pacino' })).not.toBeInTheDocument();
  });

  it('renders a plain GET search field so pressing Search/Enter filters the list', () => {
    render(PersonSearch, { props: { query: 'Pacino' } });
    const form = screen.getByRole('search', { name: 'Search people' });
    expect(form).toHaveAttribute('method', 'GET');
    expect(screen.getByRole('combobox')).toHaveAttribute('name', 'q');
    expect(screen.getByRole('combobox')).toHaveValue('Pacino');
    expect(screen.getByRole('button', { name: 'Search' })).toHaveAttribute('type', 'submit');
    expect(screen.getByRole('link', { name: 'Clear' })).toHaveAttribute('href', '/people');
  });

  it('clamps a long query to 100 characters before requesting suggestions', async () => {
    const suggestFn = vi.fn(async (_q: string): Promise<Hit[]> => []);
    render(PersonSearch, { props: { suggestFn, debounceMs: 10 } });

    const box = screen.getByRole('combobox');
    await fireEvent.input(box, { target: { value: 'a'.repeat(150) } });
    await new Promise((r) => setTimeout(r, 30));

    expect(suggestFn).toHaveBeenCalledTimes(1);
    expect(suggestFn.mock.calls[0]?.[0]).toHaveLength(100);
  });
});
