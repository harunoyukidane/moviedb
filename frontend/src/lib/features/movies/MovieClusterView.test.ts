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

  it('marks the first six posters eager and the rest lazy, without waiting for a client measurement', () => {
    const many = Array.from({ length: 8 }, (_, i) => ({
      id: `m${i}`,
      title: `Movie ${i}`,
      releaseDate: null,
      artwork: { id: `a${i}`, url: `/artwork/a${i}`, mediaType: 'image/jpeg', byteSize: 100 }
    })) as any;
    render(MovieClusterView, { props: { items: many } });
    const imgs = screen.getAllByRole('img');
    imgs.slice(0, 6).forEach((img) => expect(img).not.toHaveAttribute('loading', 'lazy'));
    imgs.slice(6).forEach((img) => expect(img).toHaveAttribute('loading', 'lazy'));
  });

  it('renders nothing but an empty, labeled list for an empty page', () => {
    render(MovieClusterView, { props: { items: [] } });
    const list = screen.getByRole('list', { name: 'Movies' });
    expect(list.children.length).toBe(0);
  });
});
