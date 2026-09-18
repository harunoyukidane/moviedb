import { render, screen } from '@testing-library/svelte';
import { describe, expect, it, vi } from 'vitest';

// A minimal hand-rolled store (no 'svelte/store' import) so this file avoids
// vitest's ESM/CJS require restriction inside a vi.hoisted factory.
const pageStore = vi.hoisted(() => {
  let value: { status: number; error: { code?: string; message: string } | null } = {
    status: 200,
    error: null
  };
  const subs = new Set<(v: typeof value) => void>();
  return {
    subscribe(run: (v: typeof value) => void) {
      run(value);
      subs.add(run);
      return () => subs.delete(run);
    },
    set(v: typeof value) {
      value = v;
      subs.forEach((run) => run(value));
    }
  };
});

vi.mock('$app/stores', () => ({ page: pageStore }));

import ErrorPage from './+error.svelte';

describe('+error.svelte', () => {
  it('renders a 404 inside the app shell with the server message', () => {
    pageStore.set({ status: 404, error: { code: 'NOT_FOUND', message: "We couldn't find what you were looking for." } });
    render(ErrorPage);
    expect(screen.getByRole('heading', { name: 'Not found' })).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent("We couldn't find what you were looking for.");
  });

  it('renders a 503 with the dependency-unavailable message', () => {
    pageStore.set({
      status: 503,
      error: { code: 'DEPENDENCY_UNAVAILABLE', message: 'A required service is temporarily unavailable. Please try again in a moment.' }
    });
    render(ErrorPage);
    expect(screen.getByRole('heading', { name: 'Service unavailable' })).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent(/temporarily unavailable/i);
  });

  it('links back to the movies list', () => {
    pageStore.set({ status: 404, error: { message: 'gone' } });
    render(ErrorPage);
    expect(screen.getByRole('link', { name: 'Back to movies' })).toHaveAttribute('href', '/movies');
  });
});
