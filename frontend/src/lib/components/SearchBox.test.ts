import { render, screen, waitFor } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import SearchBox from './SearchBox.svelte';

type Results = { movies: any[]; people: any[]; error?: string };

describe('SearchBox', () => {
  it('debounces rapid keystrokes into a single request', async () => {
    const user = userEvent.setup();
    const searchFn = vi.fn(async (_q: string): Promise<Results> => ({ movies: [], people: [] }));
    render(SearchBox, { props: { searchFn, debounceMs: 50 } });

    const box = screen.getByRole('searchbox');
    await user.type(box, 'matrix'); // 6 keystrokes in quick succession

    // wait past the debounce window
    await new Promise((r) => setTimeout(r, 120));
    // only one request for the settled value
    expect(searchFn).toHaveBeenCalledTimes(1);
    expect(searchFn.mock.calls[0]?.[0]).toBe('matrix');
  });

  it('renders results after a successful search', async () => {
    const user = userEvent.setup();
    const searchFn = vi.fn(async (): Promise<Results> => ({
      movies: [{ id: 'm1', title: 'The Matrix', releaseDate: '1999-03-31', matchedPersonNames: ['Keanu'] }],
      people: [{ id: 'p1', name: 'Keanu Reeves' }]
    }));
    render(SearchBox, { props: { searchFn, debounceMs: 10 } });

    await user.type(screen.getByRole('searchbox'), 'matrix');
    await waitFor(() => expect(screen.getByTestId('search-results')).toBeInTheDocument());
    expect(screen.getByText('The Matrix')).toBeInTheDocument();
    expect(screen.getByText('Keanu Reeves')).toBeInTheDocument();
    // matched person names surfaced for the movie hit
    expect(screen.getByText(/with Keanu/)).toBeInTheDocument();
  });

  it('shows an empty state when there are no matches', async () => {
    const user = userEvent.setup();
    const searchFn = vi.fn(async (): Promise<Results> => ({ movies: [], people: [] }));
    render(SearchBox, { props: { searchFn, debounceMs: 10 } });
    await user.type(screen.getByRole('searchbox'), 'zzz');
    await waitFor(() => expect(screen.getByText(/No matches/)).toBeInTheDocument());
  });

  it('ignores a stale response so a newer query always wins (token guard)', async () => {
    const user = userEvent.setup();
    // First call resolves slowly with stale data; second resolves fast with fresh data.
    let call = 0;
    const resolvers: Array<(r: Results) => void> = [];
    const searchFn = vi.fn(
      (_q: string) =>
        new Promise<Results>((resolve) => {
          resolvers.push(resolve);
          call++;
        })
    );
    render(SearchBox, { props: { searchFn, debounceMs: 10 } });

    const box = screen.getByRole('searchbox');
    await user.type(box, 'old');
    await new Promise((r) => setTimeout(r, 30)); // fire first request
    await user.clear(box);
    await user.type(box, 'new');
    await new Promise((r) => setTimeout(r, 30)); // fire second request

    expect(searchFn).toHaveBeenCalledTimes(2);
    // resolve the NEWER (second) request first, then the stale (first) one
    resolvers[1]({ movies: [{ id: 'n', title: 'New Result', releaseDate: null, matchedPersonNames: [] }], people: [] });
    await waitFor(() => expect(screen.getByText('New Result')).toBeInTheDocument());
    resolvers[0]({ movies: [{ id: 'o', title: 'Old Result', releaseDate: null, matchedPersonNames: [] }], people: [] });
    // the stale response must NOT replace the newer results
    await new Promise((r) => setTimeout(r, 20));
    expect(screen.queryByText('Old Result')).not.toBeInTheDocument();
    expect(screen.getByText('New Result')).toBeInTheDocument();
  });
});
