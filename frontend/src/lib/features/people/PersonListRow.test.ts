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

  it('defaults to lazy-loading with no fetchpriority when no index is given', () => {
    render(PersonListRow, { props: { id: 'p1', name: 'Jane Star', photoUrl: '/api/people/p1/photo' } });
    const img = screen.getByAltText('Photo of Jane Star');
    expect(img).toHaveAttribute('loading', 'lazy');
    expect(img).not.toHaveAttribute('fetchpriority');
  });

  it('eager-loads and does not lazy-load within the first row (index < 6)', () => {
    render(PersonListRow, { props: { id: 'p1', name: 'Jane Star', photoUrl: '/api/people/p1/photo', index: 3 } });
    const img = screen.getByAltText('Photo of Jane Star');
    expect(img).not.toHaveAttribute('loading');
    expect(img).not.toHaveAttribute('fetchpriority');
  });

  it('gives only the very first photo fetchpriority=high', () => {
    render(PersonListRow, { props: { id: 'p1', name: 'Jane Star', photoUrl: '/api/people/p1/photo', index: 0 } });
    const img = screen.getByAltText('Photo of Jane Star');
    expect(img).not.toHaveAttribute('loading');
    expect(img).toHaveAttribute('fetchpriority', 'high');
  });

  it('shows the shared fallback (no attempted image load) when photoUrl is absent', () => {
    render(PersonListRow, { props: { id: 'p1', name: 'Jane Star', photoUrl: null } });
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
    expect(document.querySelector('.person-photo-fallback')).toBeInTheDocument();
  });

  it('omits the dates span when absent', () => {
    render(PersonListRow, { props: { id: 'p1', name: 'Jane Star' } });
    expect(document.querySelector('.card-dates')).toBeNull();
  });
});
