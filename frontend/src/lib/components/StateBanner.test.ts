import { render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import StateBanner from './StateBanner.svelte';

describe('StateBanner', () => {
  it('renders an alert role for errors (dependency/validation error state)', () => {
    render(StateBanner, { props: { variant: 'error' } });
    expect(screen.getByRole('alert')).toBeInTheDocument();
  });

  it('renders a status role for info/empty state', () => {
    render(StateBanner, { props: { variant: 'info' } });
    expect(screen.getByRole('status')).toBeInTheDocument();
  });
});
