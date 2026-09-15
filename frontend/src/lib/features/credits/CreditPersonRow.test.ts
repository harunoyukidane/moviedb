import { render, screen, fireEvent } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import CreditPersonRow from './CreditPersonRow.svelte';

describe('CreditPersonRow', () => {
  it('shows the photo, name, and role text for an available person', () => {
    render(CreditPersonRow, {
      props: { personId: 'p1', personName: 'Jane Star', available: true, roleText: 'Cobb' }
    });
    const img = screen.getByAltText('Photo of Jane Star');
    expect(img).toHaveAttribute('src', '/api/people/p1/photo');
    expect(screen.getByText('Jane Star')).toBeInTheDocument();
    expect(screen.getByText('Cobb')).toBeInTheDocument();
  });

  it('falls back to the shared placeholder when the photo fails to load (e.g. 404, no photo)', async () => {
    render(CreditPersonRow, {
      props: { personId: 'p1', personName: 'Jane Star', available: true, roleText: 'Cobb' }
    });
    const img = screen.getByAltText('Photo of Jane Star');
    await fireEvent.error(img);
    expect(screen.queryByAltText('Photo of Jane Star')).not.toBeInTheDocument();
    expect(document.querySelector('.person-photo-fallback')).toBeInTheDocument();
  });

  it('shows "Unknown person" and the shared placeholder for an unavailable person, without requesting a photo', () => {
    render(CreditPersonRow, {
      props: { personId: 'p1', personName: 'Ghost', available: false, roleText: 'Villain' }
    });
    expect(screen.getByText('Unknown person')).toBeInTheDocument();
    expect(screen.queryByText('Ghost')).not.toBeInTheDocument();
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
    expect(document.querySelector('.person-photo-fallback')).toBeInTheDocument();
  });

  it('omits the role line when roleText is empty', () => {
    render(CreditPersonRow, {
      props: { personId: 'p1', personName: 'Jane Star', available: true, roleText: '' }
    });
    expect(document.querySelector('.credit-role')).toBeNull();
  });
});
