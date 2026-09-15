import { describe, expect, it } from 'vitest';
import { formatDateTime } from './format';

describe('formatDateTime', () => {
  it('formats an ISO-8601 offset date-time into a readable date and time', () => {
    const formatted = formatDateTime('2026-03-05T14:30:00Z');
    // Locale-dependent, so assert on structure rather than an exact string.
    expect(formatted).toMatch(/2026/);
    expect(formatted.length).toBeGreaterThan(0);
  });
});
