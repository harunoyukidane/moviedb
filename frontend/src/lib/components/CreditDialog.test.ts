import { render, screen } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { tick } from 'svelte';
import CreditDialog from './CreditDialog.svelte';
import type { CreditRoleCode } from '$lib/server/types';

const roles: CreditRoleCode[] = [
  { code: 'ACTOR', title: 'Actor', category: 'CAST', department: 'Acting', description: '', active: true },
  { code: 'DIRECTOR', title: 'Director', category: 'CREW', department: 'Directing', description: '', active: true }
];

describe('CreditDialog', () => {
  beforeEach(() => {
    // stub the BFF autocomplete endpoint
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        new Response(JSON.stringify({ items: [{ id: '11111111-1111-1111-1111-111111111111', name: 'Jane Star' }] }), {
          status: 200,
          headers: { 'content-type': 'application/json' }
        })
      )
    );
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('is an accessible modal dialog', async () => {
    render(CreditDialog, { props: { open: true, roles } });
    await tick();
    expect(screen.getByRole('dialog')).toHaveAttribute('aria-modal', 'true');
    expect(screen.getByRole('heading', { name: 'Add credit' })).toBeInTheDocument();
  });

  it('requires a character name for CAST roles before enabling submit', async () => {
    const user = userEvent.setup();
    render(CreditDialog, { props: { open: true, roles } });
    await tick();

    const submit = screen.getByRole('button', { name: 'Add credit' });
    expect(submit).toBeDisabled();

    // pick a person via autocomplete
    await user.type(screen.getByLabelText('Person'), 'Jane');
    // debounce is 200ms
    await new Promise((r) => setTimeout(r, 260));
    await user.click(await screen.findByRole('button', { name: 'Jane Star' }));

    // choose a CAST role -> character field appears and submit stays disabled until filled
    await user.selectOptions(screen.getByLabelText('Role'), 'ACTOR');
    expect(screen.getByLabelText('Character name *')).toBeInTheDocument();
    expect(submit).toBeDisabled();

    await user.type(screen.getByLabelText('Character name *'), 'Cobb');
    expect(submit).toBeEnabled();
  });

  it('CREW roles do not require a character name', async () => {
    const user = userEvent.setup();
    render(CreditDialog, { props: { open: true, roles } });
    await tick();

    await user.type(screen.getByLabelText('Person'), 'Jane');
    await new Promise((r) => setTimeout(r, 260));
    await user.click(await screen.findByRole('button', { name: 'Jane Star' }));

    await user.selectOptions(screen.getByLabelText('Role'), 'DIRECTOR');
    // no character field for CREW
    expect(screen.queryByLabelText('Character name *')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add credit' })).toBeEnabled();
  });

  it('shows an inline message when the person search request fails (F17)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new Error('network down');
      })
    );
    const user = userEvent.setup();
    render(CreditDialog, { props: { open: true, roles } });
    await tick();

    await user.type(screen.getByLabelText('Person'), 'Jane');
    await new Promise((r) => setTimeout(r, 260));

    expect(await screen.findByRole('alert')).toHaveTextContent(/couldn't search people/i);
    // the spinner clears rather than sticking around forever
    expect(screen.queryByText('Searching…')).not.toBeInTheDocument();
  });
});
