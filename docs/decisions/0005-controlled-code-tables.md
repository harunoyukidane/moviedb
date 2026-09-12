# ADR-5: Controlled genre/credit-role code tables; roles stored on the credit

Status: accepted

## Context
Genres and credit roles are a controlled vocabulary that should be consistent,
orderable, and retireable without breaking historical data. A role is a property
of a specific credit (this person directed this movie), not of the person.

## Decision
Model genres and credit roles as controlled code tables (`genre_code`,
`credit_role_code`, §7.2) with `active` and `display_order`. Store the role on the
`movie_credit` row (`role_code` + `category`), not on the person. Assigning an
inactive or unknown code is rejected.

## Evidence (phase 3)
- Entities `GenreCode`, `CreditRoleCode`; read APIs `ReferenceUseCases.listGenres`
  / `listCreditRoles(category, activeOnly)`; GraphQL `genres`/`creditRoles`
  queries with `activeOnly` (default true) and `category` filters.
- `CreditRules.requireActiveCode` rejects missing/inactive codes; verified by
  `CreditRulesTest` and by `CatalogueGraphQlIntegrationTest`
  (`genres query returns only active by default`, `creditRoles filter by category`).
- Roles live on `movie_credit` (ADR-2 evidence), never on the person.

## Consequences
- Retiring a code (`active = false`) hides it from pickers without invalidating
  existing credits.
- Reference reads are cheap and orderable via `display_order`.
