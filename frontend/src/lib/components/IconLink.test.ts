import { render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import IconLink from './IconLink.svelte';

describe('IconLink', () => {
  it('is a real link with an accessible name and matching tooltip', () => {
    render(IconLink, { props: { icon: 'edit', href: '/movies/m1/edit', label: 'Edit movie' } });
    const link = screen.getByRole('link', { name: 'Edit movie' });
    expect(link).toHaveAttribute('href', '/movies/m1/edit');
    expect(link).toHaveAttribute('title', 'Edit movie');
  });
});
