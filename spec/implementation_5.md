# Implementation Phase 5 — Frontend BFF + UI CRUD

Status: ready to execute
Depends on: phase 3 (GraphQL API), phase 4 (artwork endpoints)
Reference: TECHNICAL_SPECIFICATION v0.1.md §1/§5 (BFF), §8.1 (SDL), §8.2 (errors), §11 (UI/interaction), §14 (security)

## Objective

Build the SvelteKit/TypeScript frontend as a server-side BFF: the browser calls SvelteKit server routes, and SvelteKit calls the Catalogue GraphQL API server-side. Implement the movie/person/credit/artwork CRUD screens with the required UI states, accessibility basics, and responsive layout. Search UI is phase 6.

## BFF pattern (confirmed decision 3b)
- Browser never calls GraphQL directly. All GraphQL requests go through SvelteKit `+page.server.ts` / `+server.ts` (form actions, load functions, or server endpoints).
- `graphql-request` + generated TypeScript types (codegen from the committed SDL) used **server-side only**.
- A correlation ID is generated/forwarded server-side into the GraphQL HTTP header (which the backend propagates into gRPC).
- No browser-to-backend CORS; only SvelteKit's own origin is exposed. Artwork bytes are served by the Catalogue media endpoints; the BFF supplies/relays URLs.
- Single error-to-human-message boundary: map stable `extensions.code` values (§8.2) to friendly messages in one module, not ad hoc per component.

## Routes / information architecture (§11.1)
`/` or `/movies` (catalogue + search entry), `/movies/new`, `/movies/[id]`, `/movies/[id]/edit`, `/people`, `/people/new`, `/people/[id]`, `/people/[id]/edit`, `/about` (phase 8 attribution). Search entry is present but wired in phase 6.

## Movie editor behaviour (§11.2)
- Movie fields and artwork are visually separate sections.
- Cast / Creators tabs; genres multi-select from active `GenreCode`; selected genres shown as tags.
- "Add credit" opens an accessible dialog with person autocomplete + category-specific fields; selected `CreditRoleCode` determines CAST/CREW; CAST requires character name; billing order optional, non-negative.
- "Remove" reads "Remove from movie," never "Delete person."
- No matching person -> link to create the person first (no silent duplicate creation).
- Destructive actions confirm and maintain keyboard focus.

## UI state rules (§11.3)
Every remote screen supports loading, empty, success, validation-error, and dependency-error states. Mutations disable duplicate submission. Requests carry their query/token so stale responses cannot overwrite newer ones. Forms preserve input after server-side errors. GraphQL errors converted to human messages at the single boundary.

## Visual direction (§11.4)
Restrained cinema aesthetic; poster grid; one accent colour; 8-point spacing; mobile-first. WCAG 2.2 AA basics: semantic headings, labels, visible focus, keyboard dialogs, contrast, alt text, reduced-motion respect.

## Tasks (in order)
1. SvelteKit app scaffold in `frontend/` (Vite, TypeScript strict, Vitest + Svelte Testing Library, Playwright config stub for phase 8).
2. GraphQL codegen wired to the committed SDL; server-only client module; correlation-ID injection.
3. Error-mapping boundary module (code -> message) + tests.
4. Movie list + detail (server load), poster rendering from media URL, pagination, empty/loading/error states.
5. Movie create/edit forms via server form actions; validation-error surfacing; artwork upload UI (progress feedback for upload).
6. Credit management dialog (autocomplete, CAST/CREW fields, remove-from-movie) with accessible focus handling.
7. People list/detail/create/edit; person photo upload; person delete surfaces `PERSON_IN_USE` clearly.
8. Genre multi-select tags.

## Test requirements (Vitest + Svelte Testing Library)
- Loading / empty / success / validation-error / dependency-error render paths.
- Mutation disables duplicate submit; form preserves input after server error.
- Confirmation dialog for destructive actions; keyboard focus retained; accessible labels present.
- Error boundary maps each `extensions.code` to the correct human message.
- Upload progress feedback appears during artwork upload.
- (Stale-search cancellation lives in phase 6.)

## Demo statement (phase acceptance)
- Through the browser (via the BFF) a user can list, add, update, and delete movies and people, manage credits, and upload/replace artwork and person photos, with visible progress and clear feedback.
- No GraphQL call originates from the browser (verified: requests go to SvelteKit routes).
- Component tests pass; screens are responsive and meet the stated accessibility basics.

## ADR
- ADR-11 (SvelteKit server-side BFF) — record with evidence (no browser-origin GraphQL calls).
