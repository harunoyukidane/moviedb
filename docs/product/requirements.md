# Requirements

```yaml
status: current
canonical_for: product-scope
last_verified: 2026-09-17
```

Describes observable behavior. How it's built is in [architecture/](../architecture/overview.md);
what's left to build is in [plans/v2/](../plans/v2/README.md), [plans/v2.2/](../plans/v2.2/README.md) and [plans/v2.3/](../plans/v2.3/README.md); what's verified is in
[verification/](../verification/README.md).

## Glossary

- **Frontend**: the SvelteKit server-side-rendered app; the browser's only entry point.
- **Catalogue Service**: owns movies, credits, genres, artwork metadata; exposed over GraphQL.
- **People Service**: owns person records, referenced by Catalogue by id over gRPC.
- **Artwork Store**: the `ArtworkStore` port (`put`, `open`, `delete`, `exists`, `listKeys`) that persists validated artwork bytes behind a server-generated storage key.
- **Credit**: a `MovieCredit` linking a movie to a person, with category (CAST/CREW), role, and (for CAST) a character name.
- **Storage Key**: a server-generated identifier (UUID + safe extension); client filenames are never used to form it.

## Domain semantics

- A **Person** exists independently of any movie.
- A **MovieCredit** represents a person's role in one movie; role is not a property of the person alone. Removing a person from a movie page removes only the credit, never the person.
- Creating a credit requires selecting an existing person; if none exists, the user creates the person first on the People page.
- A **CreditRoleCode** supplies a stable role code, display title, category, and definition, stored on `MovieCredit`, not on `Person`. A **GenreCode** is controlled reference data connected to movies through `MovieGenre`. Codes are stable identifiers; titles/descriptions may evolve without rewriting relationships.
- `CAST`/`CREW` remain broad role categories; character and job details stay on the credit rather than becoming fixed movie columns.
- A person may hold several credits on the same movie (e.g. writer and director).
- People with active credits cannot be deleted — this avoids surprising, cross-catalogue data loss.
- A **Comment** belongs to one movie and is physically deleted with it. Without authentication, `authorDisplayName` is submitted display text, not an account reference; timestamps/IDs are server-generated.

## Out of scope (unless explicitly revisited)

Authentication/authorization/accounts/multi-tenancy; ratings, watchlists,
recommendations, TV programmes; comment editing/deletion/reactions/threading/moderation;
internationalized content; audit-history UI and restore; real-time
collaboration/notifications; dedicated search engine, distributed cache, message
broker, Kubernetes, public-cloud deployment; video upload/streaming and image
editing; production-grade TMDB sync after initial setup.

## Working assumptions

| Area | Baseline |
|---|---|
| Scale | Under 100,000 movies, 500,000 people, 100 concurrent interactive users |
| Performance | p95 under 500ms for normal reads on a warm local system; search under 750ms |
| Artwork | Max 5 MiB per upload; JPEG/PNG/WebP only |
| Consistency | Strong within one service transaction; no distributed transaction across services |
| Security | Trusted local user; secrets stay outside Git |
| Deletion | No legal/audit retention; physical deletion where safe |
| Deployment | Reproducible local execution via Docker Compose; v2 Compose uses MinIO |

---

## V1 — status: shipped

See [verification/v1-acceptance.md](../verification/v1-acceptance.md) for evidence.

| Capability | Behaviour / acceptance criterion |
|---|---|
| List movies | Paginated movie cards with poster, title, year, and summary |
| View movie | Movie details display cast and creators ordered predictably |
| Create/edit/delete movie | Valid data creates one movie with generated ID; stale edits rejected; delete requires confirmation and removes owned credits/artwork |
| Upload artwork | JPEG, PNG, or WebP; validated type/size; old artwork safely replaced |
| List/view/create/edit people | Paginated people list with search; person lifecycle managed independently of movie credits |
| Delete person | Rejected while the person has movie credits; succeeds once unreferenced |
| Manage credits | Add an existing person to a movie, edit their credit, or remove the association |
| Classify movies | Assign one or more controlled genre codes to a movie |
| Search | One debounced search finds movie titles and people; movie results also include movies credited to matching people |
| Demo setup | One documented command starts dependencies and imports deterministic TMDB data |
| Tests | Unit tests cover business rules/edge cases; integration tests cover DB/API boundaries; one E2E happy path |

## V2 — status: in progress

Requirement text below is the acceptance-criteria form; each requirement carries
an implementation status. See [plans/v2/README.md](../plans/v2/README.md) for the
task-level backlog and dependency order.

### Requirement 1: Action and View Icons — status: done

`lib/components/Icon.svelte`/`IconButton.svelte`/`IconLink.svelte`. See
[frontend.md](../archive/v2-implementation/frontend.md) (V2-12) and
[verification/v2-acceptance.md](../verification/v2-acceptance.md).

**Confirmed design decisions** (amend criteria 2 and 3 below):

- Credit mutation — adding and removing a credit — happens only inside the
  Credit Editor, which is reached via the edit Icon Asset control on the
  movie (or person) detail page; this matches the pre-existing structure
  where the detail page was already read-only and all mutations already
  lived on `/edit`. Within the Credit Editor, each Credit carries a delete
  Icon Asset control; there is no separate per-Credit edit Icon Asset
  control for changing an existing credit's role/character/billing —
  add + remove together are the credit editing capability. Criterion 2
  below is satisfied at the level of "the Credit Editor is reached via an
  edit Icon Asset control, and each Credit within it has a delete Icon
  Asset control," not literally per-Credit edit controls.
- Removing a credit does **not** request confirmation (criterion 3 below is
  superseded): unlike deleting a movie or a person, removing a credit is
  low-cost to reverse — the credited person's data is untouched in People,
  and the credit can be re-added from the Credit Editor at any time without
  re-entering any data. Confirmation is reserved for movie/person deletion
  (criterion 4), which destroy substantial hand-entered data.

1. THE Frontend SHALL provide Icon Assets under `frontend/src/resources` for edit, delete, list view, and cluster view.
2. WHERE a Credit is displayed in the Credit Editor, THE Frontend SHALL display edit and delete Icon Asset controls for that Credit.
3. ~~WHEN a user activates the delete Icon Asset control for a Credit, THE Frontend SHALL request confirmation before removing that Credit.~~ Superseded — see confirmed design decisions above.
4. THE Frontend SHALL render the movie delete action as a labeled confirmation dialog that states the movie title and warns that deletion is permanent, rather than as an icon-only control.
5. THE Frontend SHALL provide a text alternative for each Icon Asset control that names the action it performs.

### Requirement 2: Movie Listing View Mode Toggle — status: done

`MovieViewToggle.svelte`; view persisted in the `view` query param. See
[frontend.md](../archive/v2-implementation/frontend.md) (V2-09) and
[verification/v2-acceptance.md](../verification/v2-acceptance.md).

1. THE Movie Listing SHALL provide a View Mode control offering Cluster View (poster grid, default) and List View (one row per movie: poster left, title/year/genres/truncated summary right).
2. WHEN a user selects a View Mode, THE Movie Listing SHALL re-render the current set of movies without changing the current pagination offset.
3. WHILE List View and a movie has no artwork, THE Movie Listing SHALL display the existing poster fallback placeholder.

### Requirement 3: Expanded Content and Movie Filtering — status: done

`MovieFilterInput` in `schema.graphqls`; filter controls on `/movies`; expanded
genre seed and TMDB manifest. See [catalogue.md](../archive/v2-implementation/catalogue.md)
(V2-05/V2-06/V2-07) and [verification/v2-acceptance.md](../verification/v2-acceptance.md).

1. THE Catalogue Service SHALL provide a seed dataset spanning multiple distinct Genre Codes.
2. Genre and release-year filters SHALL combine with AND semantics.
3. WHEN a user changes a filter value, THE Movie Listing SHALL reset the pagination offset to the first page.
4. WHEN a filter combination matches no movies, THE Movie Listing SHALL display an empty-result message.
5. THE Movie Listing SHALL report the total count of movies matching the active filter values.

### Requirement 4: MyDramaList-Style Cast and Credits Display — status: done

`CreditPersonRow`/`CreditSection` in `lib/features/credits/`. See
[frontend.md](../archive/v2-implementation/frontend.md) (V2-10) and
[verification/v2-acceptance.md](../verification/v2-acceptance.md). Batched
person hydration (no N+1 gRPC) already existed from v1.

1. WHERE a Movie Detail displays a Credit, THE Movie Detail SHALL display the person's photo on the left and name/role on the right.
2. CAST credits show character name as the role text; CREW credits show the credit role title.
3. IF a person has no photo, THEN a placeholder image is shown; IF a person is unavailable from People Service, THEN an unavailable-person indicator is shown in place of the name.
4. Cast Credits are ordered by billing order.

### Requirement 5: Movie Comment Section — status: done

Backend (V2-13/V2-14) and frontend (V2-15) in
[catalogue.md](../archive/v2-implementation/catalogue.md)/[frontend.md](../archive/v2-implementation/frontend.md):
`movie_comment` table, entity/repository/rules, `comments`/`addMovieComment`
GraphQL fields with server-generated id/timestamp and reverse-chronological
paging, and the Movie Detail page's comment list + submit form.

1. THE Movie Detail SHALL display a comment section listing existing Comments, each with author display name, text, and creation timestamp.
2. WHEN a user submits a Comment with non-empty text, THE Catalogue Service SHALL persist it with a server-generated timestamp and display it in the section.
3. IF text is empty/whitespace-only or exceeds the maximum length, THEN the submission is rejected with a validation message/error.
4. Comments are displayed in reverse chronological order; an empty state invites the first comment.

### Requirement 6: People List Photo Display — status: done

`Person.photoUrl` in `schema.graphqls`; `PersonListRow.svelte`. See
[frontend.md](../archive/v2-implementation/frontend.md) (V2-11) and
[verification/v2-acceptance.md](../verification/v2-acceptance.md).

1. WHERE the People Listing displays a person, THE People Listing SHALL display the person's photo (or placeholder) on the left and name on the right.
2. Existing pagination controls and displayed total count are retained.

### Requirement 7: Artwork Storage Migration to MinIO — status: done

`MinioArtworkStore` implements `ArtworkStore`; see [ADR-14](../decisions/0014-minio-object-storage.md) and [verification/v2-acceptance.md](../verification/v2-acceptance.md).

1. THE media module SHALL provide a MinIO-backed `ArtworkStore` implementation.
2. Storage Keys are server-generated (UUID + safe extension), never derived from the client filename.
3. WHERE MinIO is selected by configuration, both services SHALL use it in place of the local filesystem store.
4. Existing artwork upload validation and HTTP serving behavior SHALL be preserved.

## V2.2 — status: planned

Input validation and error-message behavior, reviewed as a whole and found to
have systemic gaps. Task-level backlog, findings, exact bounds and wording:
[plans/v2.2/README.md](../plans/v2.2/README.md). Nothing below is implemented.

### Requirement 8: Specific, field-attributed validation errors — status: planned

1. WHEN a submission fails validation, THE Frontend SHALL state what is wrong
   and name the offending value, rather than a generic instruction.
2. WHEN a submission fails validation for a specific field, THE Frontend SHALL
   mark that field invalid, associate the message with it for assistive
   technology, and move focus to the first invalid field.
3. THE Frontend SHALL show the generic "check the highlighted field" copy only
   when no field-specific message is available.
4. THE Catalogue Service SHALL expose the offending field name alongside the
   stable error code, and THE People Service SHALL carry it across gRPC.
5. `INTERNAL_ERROR` responses SHALL continue to carry no cause, class name,
   stack trace, or SQL, and SHALL keep their opaque user-facing copy.

### Requirement 9: Date plausibility — status: planned

1. A movie release date SHALL NOT be before 14 October 1888, and SHALL NOT be
   more than ten years in the future - the longest lead time at which real
   films are announced. Announced ("coming soon") films remain valid.
2. A person birth date SHALL NOT be in the future, SHALL NOT be within the last
   two years, and SHALL NOT be before 1850.
3. A person death date SHALL NOT be in the future and SHALL NOT be before the
   birth date; the implied lifespan SHALL NOT exceed 130 years.
4. WHEN a date string is not a real calendar date in `YYYY-MM-DD` form, THE
   system SHALL reject it as user input, quoting the value, and SHALL NOT
   report it as an internal error.
5. A person SHALL NOT be credited on a movie released before that person's
   birth date. This SHALL be raised when the change is submitted, not while the
   user is typing, and the message SHALL name the affected person and both
   dates.
6. WHEN a movie's release date is edited, THE Catalogue Service SHALL apply
   criterion 5 to the movie's existing credits, naming up to three affected
   people and summarising any remainder.
7. WHERE the People Service is unavailable when criterion 5 or 6 is evaluated,
   THE system SHALL refuse the save as a temporarily-unavailable dependency,
   rather than saving the change unchecked.
8. No age-at-release threshold beyond "was born" SHALL be enforced or warned
   about: a credited person can legitimately have been an infant at release.
9. Date bounds SHALL be evaluated against an injected clock so boundary cases
   are testable.

### Requirement 10: Text field bounds, counting, and character classes — status: planned

1. Every stored text field SHALL have an explicit maximum length, including
   `synopsis` and `biography`, which currently have none.
2. Lengths SHALL be counted in UTF-16 code units at every layer, so the bound
   the server enforces, the bound the browser enforces, and the count shown to
   the user are the same number. A non-BMP character therefore counts as two.
   Database column bounds count code points and remain a deliberately looser
   backstop.
3. THE Frontend SHALL show a live character count on every bounded free-text
   field, and SHALL announce reaching the limit rather than silently refusing
   further input.
4. Emoji SHALL be accepted in user-authored comment content and rejected in
   catalogue metadata fields (title, name, synopsis, biography, place of birth,
   character name).
5. THE databases SHALL be UTF8-encoded by explicit configuration rather than by
   inherited default, and that encoding SHALL be asserted by a test.
6. THE system SHALL reject `U+0000`, C0/C1 control characters, bidi-override
   characters, and zero-width characters in stored text, naming the problem.
   Zero-width joiners SHALL remain permitted where emoji are permitted, since
   they are structural to emoji sequences.
7. Newline and tab SHALL be accepted in long-text fields and rejected in
   single-line fields.
8. Rejection SHALL NOT take the form of an HTML or script blocklist; output
   escaping remains the defense against injection.

### Requirement 11: Controlled country vocabulary for place of birth — status: planned

Same rationale as the v2.1 language work
([V2.1-06](../plans/v2.1/README.md#v21-06-controlled-language-reference-table--searchable-dropdown-done)):
a free-text field whose only guard was a length check.

1. THE People Service SHALL own a controlled `country_code` reference table
   (ISO 3166-1 alpha-2) and SHALL expose it for listing.
2. A person's birth country SHALL be constrained to an active code; an unknown
   or retired code SHALL be a user-input error and SHALL NOT reach the database.
3. THE Frontend SHALL offer a searchable country dropdown, matching the
   language selector's behavior, rather than free-text entry.
4. THE free-text city/region part of a place of birth SHALL be retained
   alongside the controlled country code.

### Requirement 12: Accurate error codes and statuses — status: planned

1. THE Frontend SHALL have a distinct message for every stable code the backend
   emits, including `STORAGE_UNAVAILABLE`.
2. Each failure SHALL map to its own HTTP status; an upload failure SHALL NOT
   report a storage outage as "unsupported media type", and an update failure
   SHALL NOT report a dependency outage as a conflict.
3. A malformed identifier SHALL produce "not found", not an internal error.
4. THE Frontend SHALL render its own error page for load failures, inside the
   application shell.

### Requirement 13: Concurrent edit handling — status: planned

Optimistic locking is retained; these criteria concern everything around it.

1. An update SHALL send only the fields whose values the user actually changed,
   so two people editing different fields of the same record both succeed.
2. WHEN an update is rejected as a conflict, THE Frontend SHALL preserve and
   re-render everything the user entered, and SHALL NOT require them to retype.
3. THE Frontend SHALL warn, without blocking, when the record has changed since
   the edit page was opened, rather than only after a failed submission.
4. User-facing conflict messages SHALL NOT contain internal version numbers,
   and SHALL state that the user's entries were preserved.
5. THE Catalogue and People Services SHALL continue to reject a stale write
   server-side.
6. THE system SHALL NOT lock a record while someone is editing it; concurrency
   is handled by rejection plus recovery, not by exclusion.

### Requirement 14: Seeded demo comments — status: planned

1. THE Importer SHALL seed a deterministic set of comments per movie, spanning
   short and near-limit lengths and including emoji, so the comment UI,
   pagination, and emoji rules are demonstrable on a freshly seeded stack.
2. Seeded comments SHALL carry an idempotency key so a rerun produces identical
   counts and content, preserving the importer's existing rerun property.
3. Seeded comments SHALL be written through the application's own API, never by
   direct database writes.
4. Seeded content SHALL be obviously synthetic and SHALL NOT read as real
   reviews attributed to real people.

## V2.3 — status: planned, blocked on V2.2

### Requirement 15: Injection defenses — status: planned

Sequenced after all of V2.2 is implemented, tested and verified. Backlog:
[plans/v2.3/README.md](../plans/v2.3/README.md).

1. THE Frontend SHALL send a Content-Security-Policy on HTML responses; it
   currently sends none.
2. THE Frontend SHALL send the same browser security headers at the public
   entry point that the Catalogue Service already sends on its own surface.
3. Stored text SHALL NOT be filtered by an HTML or script blocklist; escaping
   at render remains the defense, with CSP as the layer beneath it.
4. Automated tests SHALL assert that a script payload stored in a comment
   renders as escaped text, and that no unescaped-HTML or dynamic-evaluation
   construct is introduced into frontend source.
