// V2.3-02: mirror the Catalogue's SecurityHeadersFilter at the BFF, since the
// BFF is the surface a browser actually reaches. The Catalogue keeps its own
// copy for its own HTTP surface (GraphQL + media) — this is not a duplication
// to consolidate, it is defense-in-depth for a different consumer.
export function applySecurityHeaders(headers: Headers): void {
  headers.set('X-Content-Type-Options', 'nosniff');
  headers.set('X-Frame-Options', 'DENY');
  headers.set('Referrer-Policy', 'no-referrer');
  headers.set('Cross-Origin-Resource-Policy', 'same-site');
}
