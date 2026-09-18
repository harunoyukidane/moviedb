import { render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import AlphabetPager from './AlphabetPager.svelte';

describe('AlphabetPager', () => {
  it('renders all 26 letters, each linking through hrefFor', () => {
    render(AlphabetPager, { props: { hrefFor: (letter: string) => `/people?letter=${letter}` } });
    const links = screen.getAllByRole('link');
    expect(links).toHaveLength(26);
    expect(links[0]).toHaveTextContent('A');
    expect(links[0]).toHaveAttribute('href', '/people?letter=A');
    expect(links[25]).toHaveTextContent('Z');
    expect(links[25]).toHaveAttribute('href', '/people?letter=Z');
  });

  it('marks the active letter as current', () => {
    render(AlphabetPager, {
      props: { hrefFor: (letter: string) => `/people?letter=${letter}`, activeLetter: 'C' }
    });
    expect(screen.getByRole('link', { name: 'C' })).toHaveAttribute('aria-current', 'true');
    expect(screen.getByRole('link', { name: 'A' })).not.toHaveAttribute('aria-current');
  });

  it('marks no letter as current when activeLetter is null', () => {
    render(AlphabetPager, { props: { hrefFor: (letter: string) => `/people?letter=${letter}` } });
    for (const link of screen.getAllByRole('link')) {
      expect(link).not.toHaveAttribute('aria-current');
    }
  });
});
