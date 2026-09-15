import { render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import MovieListView from './MovieListView.svelte';

const items = [
  {
    id: 'm1',
    title: 'The Matrix',
    releaseDate: '1999-03-31',
    synopsis: 'A hacker discovers the world is a simulation.',
    genres: [
      { code: 'ACTION', title: 'Action', description: '', active: true },
      { code: 'THRILLER', title: 'Thriller', description: '', active: true }
    ],
    artwork: { id: 'a1', url: '/artwork/a1', mediaType: 'image/jpeg', byteSize: 100 }
  },
  { id: 'm2', title: 'No Poster Movie', releaseDate: null, synopsis: '', genres: [], artwork: null }
] as any;

describe('MovieListView', () => {
  it('renders one row per movie, linking to the movie detail page', () => {
    render(MovieListView, { props: { items } });
    const link = screen.getByRole('link', { name: /The Matrix/ });
    expect(link).toHaveAttribute('href', '/movies/m1');
    expect(screen.getByAltText('Poster for The Matrix')).toBeInTheDocument();
    expect(screen.getByText('1999')).toBeInTheDocument();
  });

  it('shows genres and a truncated synopsis', () => {
    render(MovieListView, { props: { items } });
    expect(screen.getByText('Action, Thriller')).toBeInTheDocument();
    expect(screen.getByText('A hacker discovers the world is a simulation.')).toBeInTheDocument();
  });

  it('omits the genre line and synopsis paragraph when absent', () => {
    render(MovieListView, { props: { items } });
    const link = screen.getByRole('link', { name: /No Poster Movie/ });
    expect(link.querySelector('.row-genres')).toBeNull();
    expect(link.querySelector('.row-synopsis')).toBeNull();
  });

  it('shows the shared poster fallback when a movie has no artwork', () => {
    render(MovieListView, { props: { items } });
    const link = screen.getByRole('link', { name: /No Poster Movie/ });
    expect(link.querySelector('.poster-fallback')).toBeInTheDocument();
    expect(link.querySelector('img')).toBeNull();
  });
});
