import { render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import MovieListToolbar from './MovieListToolbar.svelte';

const genres = [{ code: 'HORROR', title: 'Horror', description: '', active: true }];
const years = [2021, 2020];

describe('MovieListToolbar', () => {
  it('composes the search box and the movie filters together', () => {
    render(MovieListToolbar, { props: { genres, years } });
    expect(screen.getByRole('searchbox')).toBeInTheDocument();
    expect(screen.getByRole('form', { name: 'Filter movies' })).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: 'Genre' })).toBeInTheDocument();
  });

  it('forwards the current filter selection to MovieFilters', () => {
    render(MovieListToolbar, { props: { genres, years, selectedGenre: 'HORROR', selectedYear: 2020 } });
    expect(screen.getByRole('combobox', { name: 'Genre' })).toHaveValue('HORROR');
    expect(screen.getByRole('combobox', { name: 'Release year' })).toHaveValue('2020');
  });
});
