# ADR-6: Physical deletion with reference protection

Status: accepted

## Context
Under the no-retention assumption the system deletes physically rather than
soft-deleting. But deleting a person who is still credited on a movie would leave
dangling references across the service boundary (People owns people; Catalogue
owns credits). Removing a person from a movie is conceptually a credit removal,
not a person deletion.

## Decision
- Deleting a **movie** physically removes it and cascades to its credits, genres,
  and artwork (`ON DELETE CASCADE`, §7.2).
- Deleting a **person** is guarded by reference protection: if any local
  `movie_credit` references the person, the delete is rejected with
  `PERSON_IN_USE`; only when unreferenced does the Catalogue call People
  `DeletePerson`. Removing a person from a movie is done via `removeMovieCredit`
  and never deletes the person.

## Evidence (phase 3)
- `PersonUseCases.deletePerson` checks `CreditRepository.existsByPersonId` and
  throws `PersonInUseException` before any gRPC delete; `PersonUseCasesTest`
  verifies both the rejection and the unreferenced-proceeds path.
- `MovieUseCases.deleteMovie` relies on the DB cascade; verified by
  `CatalogueRepositoryIntegrationTest` (`genre association and cascade delete
  removes children`).
- End-to-end: `CatalogueGraphQlIntegrationTest` (`person in use blocks delete then
  succeeds after credit removed`) shows PERSON_IN_USE, then success once the credit
  is removed, and that `removeMovieCredit` leaves the person intact.

## Consequences
- No dangling cross-service references; deletes are safe and explicit.
- The client gets a clear, stable `PERSON_IN_USE` code to guide the UI.
