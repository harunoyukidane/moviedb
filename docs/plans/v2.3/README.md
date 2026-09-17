# V2.3: injection hardening

```yaml
status: current
canonical_for: v2.3-backlog
last_verified: 2026-09-17
```

**Blocked by [plans/v2.2/README.md](../v2.2/README.md).** Do not start this
until every v2.2 item is implemented, tested and verified. Nothing here fixes a
live defect; it is defense-in-depth layered *beneath* protections that
currently hold, and mixing it into the v2.2 slice would make that slice harder
to review and harder to sign off.

Scope came out of the injection audit run during the v2.2 review — see
[Injection: what was checked, and what is actually missing](../v2.2/README.md#injection-what-was-checked-and-what-is-actually-missing)
for the full audit, kept there because it is what justifies allowing emoji into
comment fields.

## Status

| Item | Area | Status |
|---|---|---|
| [V2.3-01](#v23-01-content-security-policy-at-the-bff) | A Content-Security-Policy at the BFF | ☐ blocked on v2.2 |
| [V2.3-02](#v23-02-security-headers-at-the-public-entry-point) | Security headers at the public entry point | ☐ blocked on v2.2 |
| [V2.3-03](#v23-03-standing-injection-tests) | Standing injection tests | ☐ blocked on v2.2 |

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

## V2.3-01: Content-Security-Policy at the BFF

Add a `kit.csp` block to `svelte.config.js` with `mode: 'auto'`, so SvelteKit
emits nonces for its own inline scripts rather than forcing `'unsafe-inline'`:

- `default-src 'self'`
- `object-src 'none'`
- `base-uri 'self'`
- `frame-ancestors 'none'`
- `img-src 'self' data:` — artwork and person photos are served same-origin
  through the BFF media proxy, so no remote image host is needed

**Verify the media paths under it before merging.** `/api/artwork/[artworkId]`
and `/api/people/[personId]/photo` are the highest-risk thing to break, and a
CSP that silently stops images loading is worse than no CSP. Check both the
present-image and fallback paths.

Start in `Content-Security-Policy-Report-Only` for one release if anything
looks marginal, then switch to enforcing. Report-only is a legitimate rollout
step, not an excuse to stop half-way — pick a release to flip it and record it
here.

## V2.3-02: security headers at the public entry point

Mirror the Catalogue's `SecurityHeadersFilter` set at the BFF, via
`hooks.server.ts`, since that is the surface a browser actually reaches. The
Catalogue keeps its own copy: the two are defense-in-depth for different
consumers, not a duplication to consolidate.

## V2.3-03: standing injection tests

Turn the one-off audit into tests, so it cannot silently rot:

- **Stored XSS:** post a comment whose text *and* author display name are
  script payloads, then assert the rendered page contains them as escaped text
  and that no script executes. This is the natural companion to v2.2's emoji
  round-trip test — both prove the comment path stores exactly what it was
  given and renders it safely.
- **Source guard:** assert no `.svelte` file introduces `{@html}` and no
  frontend source introduces `innerHTML`, `outerHTML`, `eval(` or
  `new Function`. Cheap, and the kind of check that only ever fails on the day
  it matters.
- **Header test:** assert the CSP and the security headers are present on a BFF
  HTML response.

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
