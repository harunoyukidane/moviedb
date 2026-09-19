# ADR-15: Put an edge reverse proxy in front of the BFF for response compression

Status: implemented
Date: 2026-09-19

## Context

The SvelteKit BFF is the browser's entry point (ADR-11). Running under
`adapter-node`, its built-in server precompresses and serves *static* assets
(JS/CSS chunks) with gzip/brotli, but streams SSR'd HTML documents out
**uncompressed** — `adapter-node` has no document-level compression, and adding
one inside the Node process would mean compressing a streamed response in
application code.

A Lighthouse pass against the running stack flagged exactly this ("no
compression applied on the document request"). The HTML document is the
critical-path resource: it blocks first paint and is the largest uncompressed
payload on a cold load.

The alternatives considered were: compress inside the Node server with a
middleware (puts CPU work in the same process that does SSR, and fights
SvelteKit's streaming response model); accept it (leaves a measurable,
easily-fixed regression on the one request that gates first paint); or
terminate in front of the BFF.

## Decision

- Add a `proxy` service (`caddy:2.8-alpine`, config in
  [`proxy/Caddyfile`](../../proxy/Caddyfile)) as the browser's only published
  entry point. It reverse-proxies to `frontend:3000` and applies
  `encode zstd gzip` to every response, including the SSR'd document.
- Stop publishing the `frontend` container to the host. It moves onto the
  internal network only; `${FRONTEND_PORT:-4173}` now maps to the proxy's `:80`.
- Set `ADDRESS_HEADER: X-Forwarded-For` and `PROTOCOL_HEADER: X-Forwarded-Proto`
  on the BFF so `adapter-node` reads the real client IP/protocol from the proxy
  instead of seeing the proxy's own connection.
- Use zstd/gzip only. Brotli is not in the stock Caddy build — it needs a custom
  `xcaddy` image with the brotli plugin, which is more build surface than the
  remaining gain justifies.
- Keep the proxy free of routing, auth, and rewriting logic. It compresses and
  forwards; the BFF remains the only place that knows about backends.

## Scope limits

Plain HTTP, matching the previous setup. Real HTTP/2 to the browser needs TLS
in front, which this does not add — the proxy is positioned as the natural place
for it if TLS is ever required, but that is not in scope here.

Trusting `X-Forwarded-*` is only safe because the proxy is the sole published
entry point and the BFF is unreachable from the host. If `frontend` is ever
republished directly, those headers become client-spoofable and the
`ADDRESS_HEADER`/`PROTOCOL_HEADER` settings must be removed with it.

## Consequences

- One more container, image, and health surface in the Compose topology. The
  proxy currently has **no healthcheck**; `setup` waits on `frontend` health and
  the proxy starts after it, so a proxy that is up but not yet listening is a
  narrow startup race rather than a silent failure.
- The BFF is no longer reachable from the host, so anything that previously
  curled `localhost:4173` expecting to hit Node directly now goes through Caddy.
- Compression moves out of application code entirely; SSR streaming is
  unaffected because Caddy compresses the stream as it passes through.
- `ORIGIN` stays `http://localhost:${FRONTEND_PORT:-4173}` — the browser-visible
  origin is unchanged, so CSRF origin checks and the CSP are untouched.
- Frontend dev outside Docker (`npm run preview`) does not involve the proxy and
  therefore does not compress documents. Lighthouse runs must go through the
  Compose stack to reflect production behaviour.
