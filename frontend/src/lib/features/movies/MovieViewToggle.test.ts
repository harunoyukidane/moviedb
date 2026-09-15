import { render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import MovieViewToggle from './MovieViewToggle.svelte';

describe('MovieViewToggle', () => {
  it('exposes two accessible, independently named links to each view', () => {
    render(MovieViewToggle, {
      props: { view: 'cluster', clusterHref: '/movies', listHref: '/movies?view=list' }
    });
    expect(screen.getByRole('group', { name: 'Movie view' })).toBeInTheDocument();
    const cluster = screen.getByRole('link', { name: 'Cluster view' });
    const list = screen.getByRole('link', { name: 'List view' });
    expect(cluster).toHaveAttribute('href', '/movies');
    expect(list).toHaveAttribute('href', '/movies?view=list');
  });

  it('marks the active view with aria-current, leaving the other unmarked', () => {
    render(MovieViewToggle, {
      props: { view: 'list', clusterHref: '/movies', listHref: '/movies?view=list' }
    });
    expect(screen.getByRole('link', { name: 'Cluster view' })).not.toHaveAttribute('aria-current');
    expect(screen.getByRole('link', { name: 'List view' })).toHaveAttribute('aria-current', 'true');
  });

  it('preserves caller-supplied hrefs (e.g. carrying filter/offset params)', () => {
    render(MovieViewToggle, {
      props: {
        view: 'cluster',
        clusterHref: '/movies?genreCode=HORROR',
        listHref: '/movies?genreCode=HORROR&view=list'
      }
    });
    expect(screen.getByRole('link', { name: 'Cluster view' })).toHaveAttribute(
      'href',
      '/movies?genreCode=HORROR'
    );
    expect(screen.getByRole('link', { name: 'List view' })).toHaveAttribute(
      'href',
      '/movies?genreCode=HORROR&view=list'
    );
  });
});
