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
