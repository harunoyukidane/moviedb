# ADR-1: Split Catalogue and People services by data ownership

Status: accepted

## Context
Movies and people are distinct aggregates with different lifecycles, query
patterns, and editing cadence. A single service and schema would couple them and
make either side's evolution risk the other. The system needs a clear ownership
boundary so each service owns its data and exposes it only through an explicit
contract.

## Decision
Two services, each owning its own PostgreSQL database:

- **People Service** is the authoritative owner of people. It exposes an internal
  gRPC API only (§8.3) and knows nothing about movies or how credits are stored.
- **Catalogue Service** owns movies, credits, genres, and artwork. It references a
  person only by that person's ID and resolves details over gRPC (phase 3).

People never queries the Catalogue database, and Catalogue never queries the
People database; cross-service reads go through the gRPC contract.

## Evidence (phase 2)
The People Service has zero Catalogue coupling:

- No source file under `backend/people-service/src` imports any `catalogue`
  package, nor references `Movie`, `Credit`, or the Catalogue database. The only
  occurrences of the string "catalogue" are the shared organization namespace
  `com.moviecatalogue.people.*` and the group id.
- People's persistence is limited to its own `person` table (`PersonRepository`);
  there is no datasource, entity, or query targeting Catalogue tables.
- The gRPC surface (`PeopleService`) deals only in person concepts:
  `GetPerson`, `GetPeople`, `SearchPeople`, `CreatePerson`, `UpdatePerson`,
  `DeletePerson`.
- `DeletePerson` performs a physical delete with no knowledge of credits;
  referential protection is orchestrated by Catalogue (phase 3), keeping the
  ownership boundary intact.

## Consequences
- Each service can evolve its schema and scale independently.
- Cross-service consistency is eventual and mediated by the gRPC contract, not by
  shared tables or distributed transactions.
- A person referenced by a credit may be absent/renamed; Catalogue must tolerate
  missing person details when resolving credits (handled in phase 3).
