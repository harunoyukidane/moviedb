import { render, screen, waitFor } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import BackLink from './BackLink.svelte';
import { previousPageUrl } from '$lib/stores/navigation';

describe('BackLink (V2.8-06)', () => {
  it('goes to the fallback when there is no in-app previous page (direct link, bookmark, hard refresh)', () => {
    previousPageUrl.set(null);
    render(BackLink, { props: { fallbackHref: '/movies' } });
    expect(screen.getByRole('link')).toHaveAttribute('href', '/movies');
  });

  it('goes back to the actual previous page instead of the fixed fallback, e.g. a filtered list or the movie a credit was clicked from', () => {
    previousPageUrl.set('/movies?genre=HORROR');
    render(BackLink, { props: { fallbackHref: '/movies' } });
    expect(screen.getByRole('link')).toHaveAttribute('href', '/movies?genre=HORROR');
    previousPageUrl.set(null);
  });

  it('reflects updates to the previous-page store reactively', async () => {
    previousPageUrl.set(null);
    render(BackLink, { props: { fallbackHref: '/people' } });
    expect(screen.getByRole('link')).toHaveAttribute('href', '/people');

    previousPageUrl.set('/movies/movie-1');
    await waitFor(() => expect(screen.getByRole('link')).toHaveAttribute('href', '/movies/movie-1'));
    previousPageUrl.set(null);
  });
});
