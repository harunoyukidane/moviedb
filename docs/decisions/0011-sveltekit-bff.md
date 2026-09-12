# ADR-11: SvelteKit server-side BFF

Status: accepted

## Context
The browser needs catalogue data via GraphQL. Exposing GraphQL directly to the browser requires CORS configuration on the backend and scatters correlation-ID handling and error mapping across client code.

## Decision
The browser calls SvelteKit server routes (load functions, form actions, server endpoints). SvelteKit calls the Catalogue GraphQL API server-side (backend-for-frontend). The GraphQL client and generated types are used server-side only.

## Consequences
- No browser-to-backend CORS; only the SvelteKit origin is exposed.
- Correlation IDs are generated/propagated server-side into GraphQL and onward into gRPC.
- A single server-side error-to-human-message boundary maps stable `extensions.code` values.
- Slightly more server code, but tighter security and cleaner separation.

## Realized (phase 5)
- The GraphQL client and typed operations live under `frontend/src/lib/server/`
  (`graphql.ts`, `operations.ts`, `media.ts`). SvelteKit's build enforces that
  anything under `$lib/server/` cannot be imported into browser code — the
  production build (`npm run build`) succeeds, which is machine-checked proof that
  no client bundle pulls in `graphql-request` or any GraphQL/media call.
- All backend access goes through server routes: `+page.server.ts` load functions
  and form actions for every movie/person/credit screen, plus server endpoints
  `/api/people-search` (autocomplete) and `/api/people/[personId]/photo` (byte
  relay). The browser only ever talks to the SvelteKit origin.
- Correlation IDs: `gql()` sets `X-Correlation-ID` (generated via `randomUUID` or
  forwarded from the inbound request) so the id propagates into GraphQL and onward
  into gRPC; the media relay forwards the same header.
- Single error boundary: `frontend/src/lib/errors.ts` maps every stable
  `extensions.code` (§8.2) to a friendly message; `errors.test.ts` covers each code.
- Tooling: Node 18.14.2 on this host, so versions are pinned to SvelteKit 2 /
  Svelte 4 / Vite 5 / Vitest 1 for compatibility. 18 component/unit tests pass;
  `svelte-check` reports 0 errors.
