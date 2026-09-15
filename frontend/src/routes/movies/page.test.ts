import { render, screen } from '@testing-library/svelte';
import { describe, expect, it, vi } from 'vitest';
import { tick } from 'svelte';

// A minimal hand-rolled store (no 'svelte/store' import) so this file avoids
// vitest's ESM/CJS require restriction inside a vi.hoisted factory.
const pageStore = vi.hoisted(() => {
  let value: { url: URL } = { url: new URL('http://localhost/movies') };
  const subs = new Set<(v: { url: URL }) => void>();
  return {
    subscribe(run: (v: { url: URL }) => void) {
      run(value);
      subs.add(run);
      return () => subs.delete(run);
    },
    set(v: { url: URL }) {
      value = v;
      subs.forEach((run) => run(value));
    }
  };
});

vi.mock('$app/stores', () => ({ page: pageStore }));

import Page from './+page.svelte';

const baseData = {
  page: {
    items: [
      { id: 'm1', title: 'The Matrix', releaseDate: '1999-03-31', synopsis: 'S.', genres: [], artwork: null }
    ],
    total: 1,
    limit: 20,
    offset: 0
  },
  error: null,
  genres: [],
  years: [2020, 1999],
  filter: { genreCode: null, releaseYear: null }
} as any;

describe('/movies view toggle wiring', () => {
  it('defaults to cluster view and points the list link at ?view=list', () => {
    pageStore.set({ url: new URL('http://localhost/movies') });
    render(Page, { props: { data: baseData } });
    expect(screen.getByRole('link', { name: /The Matrix/ })).toHaveClass('poster-card');
    expect(screen.getByRole('link', { name: 'List view' })).toHaveAttribute('href', '/movies?view=list');
    expect(screen.getByRole('link', { name: 'Cluster view' })).toHaveAttribute('aria-current', 'true');
  });

  it('renders the list view and points the cluster link back to /movies when ?view=list', () => {
    pageStore.set({ url: new URL('http://localhost/movies?view=list') });
    render(Page, { props: { data: baseData } });
    expect(screen.getByRole('link', { name: /The Matrix/ })).not.toHaveClass('poster-card');
    expect(screen.getByRole('link', { name: 'Cluster view' })).toHaveAttribute('href', '/movies');
    expect(screen.getByRole('link', { name: 'List view' })).toHaveAttribute('aria-current', 'true');
  });

  it('view links and pager links both carry the active genre/year filter', () => {
    pageStore.set({ url: new URL('http://localhost/movies?genreCode=HORROR&releaseYear=2020') });
    const data = {
      ...baseData,
      filter: { genreCode: 'HORROR', releaseYear: 2020 },
      page: { ...baseData.page, total: 40, limit: 20, offset: 20 }
    };
    render(Page, { props: { data } });
    expect(screen.getByRole('link', { name: 'List view' })).toHaveAttribute(
      'href',
      '/movies?genreCode=HORROR&releaseYear=2020&view=list&offset=20'
    );
    expect(screen.getByRole('link', { name: '← Previous' })).toHaveAttribute(
      'href',
      '/movies?genreCode=HORROR&releaseYear=2020'
    );
  });

  // Regression test for a real bug caught in manual verification: Svelte's reactivity
  // tracking is per-statement and purely syntactic, so an href built by a plain
  // function call that reads `view`/`filter` via closure (instead of as an explicit
  // argument) went stale after an in-place client-side navigation that changed only
  // that value — a fresh `render()` per test does not exercise this, since it always
  // reflects the very first store value. This test updates the SAME mounted instance.
  it('recomputes the pager hrefs after the URL store updates in place (no remount)', async () => {
    const data = {
      ...baseData,
      page: { ...baseData.page, total: 40, limit: 20, offset: 0 }
    };
    pageStore.set({ url: new URL('http://localhost/movies') });
    render(Page, { props: { data } });
    expect(screen.getByRole('link', { name: 'Next →' })).toHaveAttribute('href', '/movies?offset=20');

    // Same mounted component, only the URL store changes — as happens on a real
    // client-side navigation to ?view=list.
    pageStore.set({ url: new URL('http://localhost/movies?view=list') });
    await tick();
    expect(screen.getByRole('link', { name: 'Next →' })).toHaveAttribute(
      'href',
      '/movies?view=list&offset=20'
    );
  });
});
