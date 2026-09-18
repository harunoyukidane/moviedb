import { render, screen } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import StalenessBanner from './StalenessBanner.svelte';

describe('StalenessBanner', () => {
  it('renders nothing when not visible', () => {
    render(StalenessBanner, { props: { visible: false } });
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('appears when the version has moved', () => {
    render(StalenessBanner, { props: { visible: true } });
    expect(screen.getByRole('status')).toHaveTextContent(/changed by someone else/i);
  });

  it('is dismissible', async () => {
    const user = userEvent.setup();
    render(StalenessBanner, { props: { visible: true } });
    await user.click(screen.getByRole('button', { name: 'Dismiss' }));
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });
});
