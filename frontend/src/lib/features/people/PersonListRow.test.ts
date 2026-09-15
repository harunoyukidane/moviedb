import { render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import PersonListRow from './PersonListRow.svelte';

describe('PersonListRow', () => {
  it('links to the person detail page and shows their name and dates', () => {
    render(PersonListRow, { props: { id: 'p1', name: 'Jane Star', dates: '1980–2020' } });
    const link = screen.getByRole('link', { name: /Jane Star/ });
    expect(link).toHaveAttribute('href', '/people/p1');
    expect(screen.getByText('Jane Star')).toBeInTheDocument();
    expect(screen.getByText('1980–2020')).toBeInTheDocument();
  });

  it('renders the photo when photoUrl is present', () => {
    render(PersonListRow, { props: { id: 'p1', name: 'Jane Star', photoUrl: '/api/people/p1/photo' } });
    const img = screen.getByAltText('Photo of Jane Star');
    expect(img).toHaveAttribute('src', '/api/people/p1/photo');
  });

  it('shows the shared fallback (no attempted image load) when photoUrl is absent', () => {
    render(PersonListRow, { props: { id: 'p1', name: 'Jane Star', photoUrl: null } });
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
    expect(document.querySelector('.person-photo-fallback')).toBeInTheDocument();
  });

  it('omits the dates span when absent', () => {
    render(PersonListRow, { props: { id: 'p1', name: 'Jane Star' } });
    expect(document.querySelector('.row-dates')).toBeNull();
  });
});
