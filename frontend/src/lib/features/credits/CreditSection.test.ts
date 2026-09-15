import { render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import CreditSection from './CreditSection.svelte';
import type { MovieCredit } from '$lib/server/types';

function credit(over: Partial<MovieCredit>): MovieCredit {
  return {
    id: 'c1',
    category: 'CAST',
    role: { code: 'ACTOR', title: 'Actor', category: 'CAST', department: null, description: '', active: true },
    characterName: null,
    sourceRoleName: null,
    billingOrder: null,
    person: { id: 'p1', name: 'Jane Star', available: true },
    ...over
  } as MovieCredit;
}

describe('CreditSection', () => {
  it('shows the empty-state message when there are no credits', () => {
    render(CreditSection, { props: { credits: [], emptyMessage: 'No cast yet.' } });
    expect(screen.getByText('No cast yet.')).toBeInTheDocument();
  });

  it('renders cast rows with the character name as role text', () => {
    render(CreditSection, {
      props: {
        credits: [credit({ id: 'c1', characterName: 'Cobb', person: { id: 'p1', name: 'Leo', available: true } })],
        emptyMessage: 'No cast yet.'
      }
    });
    expect(screen.getByText('Leo')).toBeInTheDocument();
    expect(screen.getByText('Cobb')).toBeInTheDocument();
  });

  it('renders crew rows with the role title as role text', () => {
    render(CreditSection, {
      props: {
        credits: [
          credit({
            id: 'c2',
            category: 'CREW',
            characterName: null,
            role: { code: 'DIRECTOR', title: 'Director', category: 'CREW', department: null, description: '', active: true },
            person: { id: 'p2', name: 'Ang', available: true }
          })
        ],
        emptyMessage: 'No creators yet.'
      }
    });
    expect(screen.getByText('Ang')).toBeInTheDocument();
    expect(screen.getByText('Director')).toBeInTheDocument();
  });

  it('preserves the given credit order rather than re-sorting (server already orders by billing order)', () => {
    render(CreditSection, {
      props: {
        credits: [
          credit({ id: 'c1', characterName: 'Second billed', billingOrder: 5, person: { id: 'p1', name: 'B', available: true } }),
          credit({ id: 'c2', characterName: 'Top billed', billingOrder: 0, person: { id: 'p2', name: 'A', available: true } })
        ],
        emptyMessage: 'No cast yet.'
      }
    });
    const names = screen.getAllByText(/^[AB]$/).map((el) => el.textContent);
    expect(names).toEqual(['B', 'A']);
  });
});
