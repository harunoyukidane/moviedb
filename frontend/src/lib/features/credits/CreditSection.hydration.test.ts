import { render } from '@testing-library/svelte';
import { describe, expect, it, vi, afterEach } from 'vitest';
import CreditSection from './CreditSection.svelte';
import type { MovieCredit } from '$lib/server/types';

// Regression coverage for V2-10: photos are same-origin proxy URLs rendered via
// plain <img src>, never fetched by application code. Rendering many credit rows
// must not issue any extra network calls per row — person data/photos are already
// present in the single batched movie query (backend DataLoader, §8.1); adding a
// per-row fetch here would reintroduce an N+1 the backend was built to avoid.
describe('CreditSection person hydration', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders many credit rows without making any fetch calls', () => {
    const fetchSpy = vi.fn();
    vi.stubGlobal('fetch', fetchSpy);

    const credits: MovieCredit[] = Array.from({ length: 8 }, (_, i) => ({
      id: `c${i}`,
      category: 'CAST',
      role: { code: 'ACTOR', title: 'Actor', category: 'CAST', department: null, description: '', active: true },
      characterName: `Role ${i}`,
      sourceRoleName: null,
      billingOrder: i,
      person: { id: `p${i}`, name: `Person ${i}`, available: true }
    }));

    render(CreditSection, { props: { credits, emptyMessage: 'No cast yet.' } });

    expect(fetchSpy).not.toHaveBeenCalled();
  });
});
