import { render, screen, waitFor } from '@testing-library/svelte';
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

  it('does not steal focus by default (V2.8-02)', () => {
    render(StateBanner, { props: { variant: 'info' } });
    expect(screen.getByRole('status')).not.toHaveFocus();
  });

  it('moves focus to itself when autofocus is set, so a save confirmation above a long form is not missed', async () => {
    render(StateBanner, { props: { variant: 'info', autofocus: true } });
    await waitFor(() => expect(screen.getByRole('status')).toHaveFocus());
  });
});
