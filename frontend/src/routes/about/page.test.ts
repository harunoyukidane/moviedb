import { render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import AboutPage from './+page.svelte';

describe('/about page', () => {
  it('shows the exact required TMDB attribution notice (§12.5)', () => {
    render(AboutPage);
    expect(
      screen.getByText('This product uses the TMDB API but is not endorsed or certified by TMDB.')
    ).toBeInTheDocument();
  });

  it('includes an architecture summary', () => {
    render(AboutPage);
    expect(screen.getByRole('heading', { name: 'Architecture' })).toBeInTheDocument();
    expect(screen.getByText(/backend-for-frontend/i)).toBeInTheDocument();
  });
});
