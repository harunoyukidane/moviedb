import { render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import CharCounter from './CharCounter.svelte';

describe('CharCounter', () => {
  it('counts in UTF-16 units so an emoji advances it by two', () => {
    const { container } = render(CharCounter, { props: { value: 'hi 😀', max: 2000 } });
    expect(container.textContent).toContain('5 / 2,000');
  });

  it('shows a neutral count well under the limit', () => {
    const { container } = render(CharCounter, { props: { value: 'short', max: 100 } });
    const counter = container.querySelector('.char-counter');
    expect(counter).not.toHaveClass('warning');
    expect(counter).not.toHaveClass('at-limit');
  });

  it('applies a warning style within the last 10% of the limit', () => {
    const { container } = render(CharCounter, { props: { value: 'a'.repeat(91), max: 100 } });
    expect(container.querySelector('.char-counter')).toHaveClass('warning');
  });

  it('applies an at-limit style and announces it politely instead of stopping input silently', () => {
    const { container } = render(CharCounter, { props: { value: 'a'.repeat(100), max: 100 } });
    expect(container.querySelector('.char-counter')).toHaveClass('at-limit');
    expect(screen.getByText('Character limit reached')).toBeInTheDocument();
  });

  it('does not announce the limit when under it', () => {
    render(CharCounter, { props: { value: 'short', max: 100 } });
    expect(screen.queryByText('Character limit reached')).not.toBeInTheDocument();
  });
});
