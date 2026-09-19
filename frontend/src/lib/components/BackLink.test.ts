import { render, screen, waitFor } from '@testing-library/svelte';
import { beforeEach, describe, expect, it } from 'vitest';
import BackLink from './BackLink.svelte';
import { recordNavigation, resetNavigationHistory } from '$lib/stores/navigation';

const u = (path: string) => new URL(path, 'http://localhost');

describe('BackLink (V2.8-06)', () => {
  beforeEach(() => resetNavigationHistory());

  it('goes to the fallback when there is no in-app previous page (direct link, bookmark, hard refresh)', () => {
    render(BackLink, { props: { fallbackHref: '/movies' } });
    expect(screen.getByRole('link')).toHaveAttribute('href', '/movies');
  });

  it('goes back to the filtered list the user was on, not the fixed fallback', () => {
    recordNavigation(u('/movies?genre=HORROR'), u('/movies/movie-1'));
    render(BackLink, { props: { fallbackHref: '/movies' } });
    expect(screen.getByRole('link')).toHaveAttribute('href', '/movies?genre=HORROR');
  });

  it('goes back to the movie a credit was clicked from', () => {
    recordNavigation(u('/movies'), u('/movies/movie-1'));
    recordNavigation(u('/movies/movie-1'), u('/people/person-1'));
    render(BackLink, { props: { fallbackHref: '/people' } });
    expect(screen.getByRole('link')).toHaveAttribute('href', '/movies/movie-1');
  });

  it('retargets as the user navigates, so it never points at the page just left', async () => {
    render(BackLink, { props: { fallbackHref: '/movies' } });
    expect(screen.getByRole('link')).toHaveAttribute('href', '/movies');

    recordNavigation(u('/movies'), u('/movies/movie-1'));
    recordNavigation(u('/movies/movie-1'), u('/movies/movie-1/edit'));
    await waitFor(() =>
      expect(screen.getByRole('link')).toHaveAttribute('href', '/movies/movie-1')
    );

    // Going back pops, so the next back goes one step further out rather than
    // straight back into the page just left.
    recordNavigation(u('/movies/movie-1/edit'), u('/movies/movie-1'));
    await waitFor(() => expect(screen.getByRole('link')).toHaveAttribute('href', '/movies'));
  });
});
