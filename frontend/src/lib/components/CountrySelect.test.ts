import { render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import CountrySelect from './CountrySelect.svelte';
import type { CountryCode } from '$lib/server/types';

const countries: CountryCode[] = [
  { code: 'US', name: 'United States', active: true },
  { code: 'FR', name: 'France', active: true }
];

describe('CountrySelect', () => {
  it('renders with the default label/name/id and prefills a selected country', () => {
    render(CountrySelect, { props: { countries, selected: 'FR' } });
    expect(screen.getByLabelText('Country')).toHaveValue('France');
    expect(
      (document.querySelector('input[type="hidden"][name="birthCountryCode"]') as HTMLInputElement).value
    ).toBe('FR');
  });
});
