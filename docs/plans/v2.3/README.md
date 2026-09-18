# V2.3: injection hardening

```yaml
status: current
canonical_for: v2.3-backlog
last_verified: 2026-09-18
```

v2.2 is fully done (see [plans/v2.2/README.md](../v2.2/README.md) status
table), so this slice is unblocked and implemented below.

Scope came out of the injection audit run during the v2.2 review — see
[Injection: what was checked, and what is actually missing](../v2.2/README.md#injection-what-was-checked-and-what-is-actually-missing)
for the full audit, kept there because it is what justifies allowing emoji into
comment fields.

## Status

| Item | Area | Status |
|---|---|---|
| [V2.3-01](#v23-01-content-security-policy-at-the-bff) | A Content-Security-Policy at the BFF | ✅ done |
| [V2.3-02](#v23-02-security-headers-at-the-public-entry-point) | Security headers at the public entry point | ✅ done |
| [V2.3-03](#v23-03-standing-injection-tests) | Standing injection tests | ✅ done |

## What the audit found

Verified by inspection during the v2.2 review, not assumed:

| Vector | Status |
|---|---|
| SQL injection | **Clean** — every repository query is JPQL with named parameters; no `nativeQuery`, no string-built SQL in either service |
| SQL `LIKE` metacharacters | **Clean** — escaped on both search paths, with `ESCAPE '\'` declared |
| Stored XSS | **Clean** — zero `{@html}` in any `.svelte` file; zero `innerHTML`, `outerHTML`, `eval(`, `new Function` or `document.write` in frontend source |
| Response header injection | **Clean** — the media proxy copies a fixed five-name allowlist |
| Content-Security-Policy | **Missing** — the subject of this plan |

The gap is narrow and specific. The Catalogue's `SecurityHeadersFilter` sets
`X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy` and
`Cross-Origin-Resource-Policy` — but the Catalogue is not the public entry
point. The **BFF** is, it is what serves HTML to browsers, and
[`frontend/svelte.config.js`](../../../frontend/svelte.config.js) configures no
`csp` block with no `hooks.server.ts` supplying one. So the application ships no
Content-Security-Policy on the one surface where a browser would enforce it.

Not currently exploitable, because the escaping above holds. Worth having for
the day someone adds an `{@html}`, pulls in a third-party script, or introduces
a DOM sink — which is exactly when nobody is thinking about CSP.

---

## V2.3-01: Content-Security-Policy at the BFF ✅

Added a `kit.csp` block to [svelte.config.js](../../../frontend/svelte.config.js)
with `mode: 'auto'`, so SvelteKit emits nonces for its own inline scripts
rather than forcing `'unsafe-inline'`:

- `default-src 'self'`
- `object-src 'none'`
- `base-uri 'self'`
- `frame-ancestors 'none'`
- `img-src 'self' data:` — artwork and person photos are served same-origin
  through the BFF media proxy, so no remote image host is needed
- `style-src-attr 'unsafe-inline'` — scoped narrowly to the `style` HTML
  attribute only (not `style-src` generally, and not `script-src`).
  [ArtworkUpload.svelte](../../../frontend/src/lib/components/ArtworkUpload.svelte)
  sets an inline `style` attribute for its progress-bar width, computed at
  runtime, which SvelteKit's own nonce/hash handling doesn't cover (that only
  applies to `<style>` blocks and script tags). The static
  `style="display: contents"` wrapper in `app.html` was moved to a CSS class
  instead of relying on this exception.

**Media paths verified before merging**, against the real Catalogue/People
services: a movie list, a movie detail page (poster image), and the movie
edit page all render with zero CSP console violations. Went straight to
enforcing rather than `Content-Security-Policy-Report-Only` — the only
violation found in testing (the inline `style` attribute above) was
diagnosed and fixed, not just observed, so there was nothing left to watch in
report-only mode.

## V2.3-02: security headers at the public entry point ✅

Mirrors the Catalogue's `SecurityHeadersFilter` set at the BFF, via
[hooks.server.ts](../../../frontend/src/hooks.server.ts) calling
[`applySecurityHeaders`](../../../frontend/src/lib/server/security-headers.ts).
The Catalogue keeps its own copy: the two are defense-in-depth for different
consumers, not a duplication to consolidate.

## V2.3-03: standing injection tests ✅

Turned the one-off audit into tests, so it cannot silently rot:

- **Stored XSS:** [CatalogueGraphQlIntegrationTest.kt](../../../backend/catalogue-service/src/test/kotlin/com/moviecatalogue/catalogue/graphql/CatalogueGraphQlIntegrationTest.kt)
  round-trips a `<script>` payload through `addMovieComment`/`comments` in both
  `text` and `authorDisplayName`, unmodified — the natural companion to
  v2.2's emoji round-trip test, both proving the comment path stores exactly
  what it was given. [CommentSection.test.ts](../../../frontend/src/lib/features/comments/CommentSection.test.ts)
  renders the same payload and asserts it lands as literal text (no `<script>`
  element created, no execution).
- **Source guard:** [source-guard.test.ts](../../../frontend/src/source-guard.test.ts)
  asserts no `.svelte` file introduces `{@html}` and no frontend source
  introduces `innerHTML`, `outerHTML`, `eval(` or `new Function`.
- **Header test:** [security-headers.spec.ts](../../../frontend/e2e/security-headers.spec.ts)
  asserts the CSP and the security headers are present on a built BFF HTML
  response (CSP is emitted by SvelteKit itself only on a real rendered
  response, so this has to be a Playwright e2e test, not a unit test).

Deliberately **not** input-side filtering: no HTML or script blocklist on
stored text. Escaping at render is the defense and it works; CSP is the layer
beneath it. A blocklist would reject legitimate titles (`<Dollars>`) while
providing no real protection. See
[Character classes](../v2.2/README.md#character-classes).

## Noted, not actionable yet

**CSV formula injection** — a field beginning `=`, `+`, `-` or `@` becoming a
live formula when opened in a spreadsheet — is not a risk today because nothing
exports data. If an export is ever added, that escaping belongs in the
exporter, not in the input rules: rejecting a title that starts with a hyphen
would be wrong.

**Homoglyph / confusable-script detection** on comment author names is
deliberately out of scope. Comments are unauthenticated, so a display name
carries no authority and impersonation is already trivial — anyone can type the
same name. Mixed-script detection would add real complexity and false positives
(legitimate names mix scripts) to defend an identity that does not exist.
Revisit only if comments ever gain authentication.
