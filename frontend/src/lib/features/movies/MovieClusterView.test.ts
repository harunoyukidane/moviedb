import { render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import MovieClusterView from './MovieClusterView.svelte';

const items = [
  {
    id: 'm1',
    title: 'The Matrix',
    releaseDate: '1999-03-31',
    artwork: { id: 'a1', url: '/artwork/a1', mediaType: 'image/jpeg', byteSize: 100 }
  },
  { id: 'm2', title: 'No Poster Movie', releaseDate: null, artwork: null }
] as any;

describe('MovieClusterView', () => {
  it('renders a poster card per movie, linking to the movie detail page', () => {
    render(MovieClusterView, { props: { items } });
    const link = screen.getByRole('link', { name: /The Matrix/ });
    expect(link).toHaveAttribute('href', '/movies/m1');
    expect(screen.getByAltText('Poster for The Matrix')).toBeInTheDocument();
  });

  it('shows the release year when present', () => {
    render(MovieClusterView, { props: { items } });
    expect(screen.getByText('1999')).toBeInTheDocument();
  });

  it('shows the shared poster fallback when a movie has no artwork', () => {
    render(MovieClusterView, { props: { items } });
    const link = screen.getByRole('link', { name: /No Poster Movie/ });
    expect(link.querySelector('.poster-fallback')).toBeInTheDocument();
    expect(link.querySelector('img')).toBeNull();
  });

  it('renders nothing but an empty, labeled list for an empty page', () => {
    render(MovieClusterView, { props: { items: [] } });
    const list = screen.getByRole('list', { name: 'Movies' });
    expect(list.children.length).toBe(0);
  });
});
