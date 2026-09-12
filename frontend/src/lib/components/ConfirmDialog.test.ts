import { render, screen } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { tick } from 'svelte';
import ConfirmDialog from './ConfirmDialog.svelte';

describe('ConfirmDialog', () => {
  it('is a modal dialog with an accessible name and focuses confirm on open', async () => {
    render(ConfirmDialog, { props: { open: true, title: 'Delete this movie?', confirmLabel: 'Delete movie' } });
    await tick();
    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(screen.getByRole('heading', { name: 'Delete this movie?' })).toBeInTheDocument();
    // confirm button receives focus for keyboard users
    expect(screen.getByRole('button', { name: 'Delete movie' })).toHaveFocus();
  });

  it('dispatches confirm when the confirm button is pressed', async () => {
    const user = userEvent.setup();
    const { component } = render(ConfirmDialog, { props: { open: true, title: 'Confirm', confirmLabel: 'Yes' } });
    const onConfirm = vi.fn();
    component.$on('confirm', onConfirm);
    await user.click(screen.getByRole('button', { name: 'Yes' }));
    expect(onConfirm).toHaveBeenCalledOnce();
  });

  it('cancels on Escape (keyboard) and dispatches cancel', async () => {
    const user = userEvent.setup();
    const { component } = render(ConfirmDialog, { props: { open: true, title: 'Confirm' } });
    const onCancel = vi.fn();
    component.$on('cancel', onCancel);
    await user.keyboard('{Escape}');
    expect(onCancel).toHaveBeenCalledOnce();
  });
});
