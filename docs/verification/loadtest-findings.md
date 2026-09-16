# Load test findings (V2-16)

```yaml
status: current
canonical_for: loadtest-findings
last_verified: 2026-09-16
```

`demo/loadtest/` (see the [README](../../README.md#load--concurrency-stress-test))
runs many concurrent workers against a small shared pool of movies/people,
forcing the same row to be amended, deleted, and amended-while-deleted by
multiple workers at once. The first runs against a real stack immediately
surfaced a genuine concurrency bug, reproducible on demand rather than a rare
flake.

## Finding: concurrent delete of the same row could surface as `INTERNAL_ERROR` instead of `NOT_FOUND`

**Symptom.** Under `demo/loadtest` with several workers racing to delete the
same synthetic movie, a small fraction of `deleteMovie` calls returned
`INTERNAL_ERROR` (mapped to a raw GraphQL internal error) instead of the
expected `NOT_FOUND`. The same shape of failure showed up on
`movie.uploadArtwork` as a bare HTTP 500 with no JSON body (i.e. it never
reached `ArtworkExceptionAdvice`'s mapping at all).

**Root cause: check-then-act (TOCTOU) races.**

1. `MovieUseCases.deleteMovie` and `PeopleApplicationService.deletePerson`
   both did:
   ```kotlin
   if (!repo.existsById(id)) throw NotFoundException(...)
   repo.deleteById(id)
   ```
   Two concurrent deletes for the same id can both pass `existsById` before
   either commits. The loser's `deleteById` then has nothing to delete;
   Spring Data's `SimpleJpaRepository.deleteById` throws
   `EmptyResultDataAccessException` in that case, which was never caught here
   and fell through to the generic exception handler as `INTERNAL_ERROR`.

2. `ArtworkUseCases.uploadMovieArtwork` checked `movies.existsById(movieId)`
   once, *before* validating/writing the upload bytes (relatively slow I/O).
   A `deleteMovie` racing in during that window let the use case reach the
   metadata `INSERT`, which violates the `artwork.movie_id` foreign key —
   surfacing as an uncaught `DataIntegrityViolationException`, i.e. a raw
   HTTP 500.

`PersonPhotoUseCases.uploadPhoto` does **not** have this bug: it re-fetches
the person with `findById(...).orElseThrow { PersonNotFoundException() }`
*inside* its transaction, so a concurrent delete is already caught and mapped
correctly. It was the reference for how the other two should behave.

**Fix (two attempts — the first was wrong, kept here as a note-to-self).**

The first fix dropped the `existsById` pre-check and instead called
`deleteById` directly, catching `EmptyResultDataAccessException`. That was
based on an incorrect assumption: in the Spring Data JPA version this project
uses, `deleteById` on a missing row is a **silent no-op** — it does not
throw. Running the full test suite caught this immediately (a `deleteMovie`
call for a random, never-created id now returned success instead of
`NOT_FOUND`) — worse than the original bug, since it had gone from "occasionally
a 500" to "always silently wrong."

The real fix uses the `@Version` column both entities already carry for
optimistic locking:

- [`MovieUseCases.deleteMovie`](../../backend/catalogue-service/src/main/kotlin/com/moviecatalogue/catalogue/application/MovieUseCases.kt)
  and
  [`PeopleApplicationService.deletePerson`](../../backend/people-service/src/main/kotlin/com/moviecatalogue/people/application/PeopleApplicationService.kt)
  now do `findById(id).orElseThrow { NotFound }`, then `delete(entity)` +
  an explicit `flush()`, catching `ObjectOptimisticLockingFailureException`.
  Hibernate scopes a versioned entity's `DELETE` to
  `WHERE id = ? AND version = ?`, so if another transaction already deleted
  (or updated) the row between our read and our delete, the statement
  matches zero rows and Hibernate raises that exception reliably — which we
  translate to a clean `NotFoundException`/`PersonNotFoundException` instead
  of a silent no-op or a leaked 500. The explicit `flush()` matters: without
  it, the `DELETE` can be deferred past this method's `try/catch` to
  transaction commit, where the exception would surface as an uncaught
  `TransactionSystemException` instead.
- [`ArtworkUseCases.uploadMovieArtwork`](../../backend/catalogue-service/src/main/kotlin/com/moviecatalogue/catalogue/artwork/ArtworkUseCases.kt)
  re-checks `movies.existsById(movieId)` *inside* the transaction, right
  before the metadata swap, and additionally catches
  `DataIntegrityViolationException` at the swap boundary — if the movie no
  longer exists at that point, both paths now throw `NotFoundException`
  instead of leaking the DB-level exception. The pre-existing fast-path check
  is kept (avoids paying for image validation/storage I/O for an obviously
  missing movie) but is no longer relied on for correctness. `ArtworkAsset`
  has no `@Version`, so the delete-with-version-guard technique above doesn't
  apply here — the FK violation is the only signal available.

**Verified by:**
- New unit tests in `ApplicationUseCasesTest.kt` (`MovieUseCasesTest`)
  covering present / absent / lost-to-a-concurrent-delete for `deleteMovie`,
  the equivalent three cases in `PeopleApplicationServiceTest`, and
  `ArtworkUseCasesTest.upload throws NotFound when the movie is deleted
  concurrently mid-transaction` (captures and invokes the real transaction
  callback to exercise the in-transaction re-check, rather than mocking the
  transaction result away).
- Running the **full** `catalogue-service`/`people-service` test suites (not
  just the new tests) is what caught the first fix attempt being wrong — a
  reminder that a race-condition fix needs the whole suite, not just
  hand-picked new cases, since the regression it introduced (silently
  succeeding instead of a controlled failure) wasn't something the new tests
  alone would have caught if the pre-existing `deleteMovie` GraphQL
  integration test hadn't been run alongside them.
- Re-running `demo/loadtest` after the fix: the same concurrent-delete and
  concurrent-upload-during-delete scenarios now consistently resolve to
  `NOT_FOUND` for the losing request, with zero `INTERNAL_ERROR`/HTTP 500s
  observed across repeated runs.

**Unrelated pre-existing failure noticed while verifying (also fixed):** the
full suite run also failed `ArtworkMinioHttpIntegrationTest.upload serve and
delete a movie poster through MinIO` on a duplicated
`X-Content-Type-Options: nosniff` response header (`["nosniff", "nosniff"]`
vs. expected `["nosniff"]`). Unrelated to the delete/upload race fixes above
— nothing in that work touches response headers.

Root cause:
[`ArtworkServingController.serve`](../../backend/catalogue-service/src/main/kotlin/com/moviecatalogue/catalogue/artwork/ArtworkServingController.kt)
explicitly added `.header("X-Content-Type-Options", "nosniff")` on its
`ResponseEntity`, but every response on `catalogue-service` already gets that
header from the global
[`SecurityHeadersFilter`](../../backend/catalogue-service/src/main/kotlin/com/moviecatalogue/catalogue/observability/SecurityHeadersFilter.kt)
(`response.setHeader(...)`, which replaces). Spring MVC writes a
`ResponseEntity`'s headers onto the servlet response via `addHeader` (which
*appends*), so the filter's value and the controller's explicit value both
landed in the response. Fixed by removing the redundant explicit header from
both response branches in `ArtworkServingController.serve` and relying on
the filter alone. `MovieArtworkController` (upload/delete) and
`people-service`'s `PersonPhotoController` were checked and don't have the
same duplication — `people-service` has no equivalent global filter, so its
explicit headers there are the only source and are not doubled.

## Not changed (out of scope)

`ArtworkUseCases.deleteMovieArtwork` and `PersonPhotoUseCases.deletePhoto`
have a structurally similar shape (load the row, then delete/clear it), but
`ArtworkAsset`/`person.profile_path` are not `@Version`-checked on delete, so
a concurrent double-delete there degrades to a harmless no-op rather than an
uncaught exception. `demo/loadtest` does not currently exercise the artwork
*delete* endpoint (only upload), so this has not been empirically confirmed
either way — flagged here for awareness, not fixed speculatively.
