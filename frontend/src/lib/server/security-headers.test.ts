import { describe, expect, it } from 'vitest';
import { applySecurityHeaders } from './security-headers';

describe('applySecurityHeaders', () => {
  it('sets the standard browser security headers', () => {
    const headers = new Headers();

    applySecurityHeaders(headers);

    expect(headers.get('X-Content-Type-Options')).toBe('nosniff');
    expect(headers.get('X-Frame-Options')).toBe('DENY');
    expect(headers.get('Referrer-Policy')).toBe('no-referrer');
    expect(headers.get('Cross-Origin-Resource-Policy')).toBe('same-site');
  });
});
