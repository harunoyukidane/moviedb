# ADR-2: PostgreSQL with a normalized MovieCredit association

Status: accepted

## Context
A movie relates to many people, each in a specific role (cast/crew), sometimes
with a character name and billing order. This many-to-many-with-attributes
relationship needs to be queryable (a movie's cast in billing order; a person's
filmography) and constrained (a cast member must have a character; crew must not;
the role's category must match the credit's).

## Decision
Use PostgreSQL and model credits as a normalized `movie_credit` association row
(§7.2) carrying `movie_id`, `person_id`, `role_code`, `category`, optional
`character_name`, `billing_order`, and provenance. Integrity is enforced by DB
constraints: a composite `(role_code, category)` FK to `credit_role_code`, a
cast/crew character CHECK, and uniqueness (`uq_movie_credit_manual`).

## Evidence (phase 3)
- Entity `MovieCredit` maps the association; `CreditRepository` provides
  `findAllByMovieId`, `findAllByMovieIdIn` (page batching), and `findAllByPersonId`
  (filmography).
- `CatalogueRepositoryIntegrationTest` and `CatalogueSchemaIntegrationTest` verify,
  against real Postgres: credit persistence, the cast/crew CHECK, the composite FK,
  uniqueness, cascade delete, and optimistic locking on `movie`.
- Domain `CreditRules` pre-validates the same invariants (cast-requires-character,
  crew-forbids-character, role/category agreement, billing order >= 0), covered by
  `CreditRulesTest`.

## Consequences
- Rich, constrained queries without denormalization.
- The DB is the final integrity authority; the domain layer mirrors the rules to
  return friendly BAD_USER_INPUT errors before hitting the constraint.
