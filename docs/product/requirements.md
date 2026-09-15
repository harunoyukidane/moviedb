# Requirements

```yaml
status: current
canonical_for: product-scope
last_verified: 2026-09-15
```

Describes observable behavior. How it's built is in [architecture/](../architecture/overview.md);
what's left to build is in [plans/v2/](../plans/v2/README.md); what's verified is in
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
[frontend.md](../plans/v2/frontend.md) (V2-12) and
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
[frontend.md](../plans/v2/frontend.md) (V2-09) and
[verification/v2-acceptance.md](../verification/v2-acceptance.md).

1. THE Movie Listing SHALL provide a View Mode control offering Cluster View (poster grid, default) and List View (one row per movie: poster left, title/year/genres/truncated summary right).
2. WHEN a user selects a View Mode, THE Movie Listing SHALL re-render the current set of movies without changing the current pagination offset.
3. WHILE List View and a movie has no artwork, THE Movie Listing SHALL display the existing poster fallback placeholder.

### Requirement 3: Expanded Content and Movie Filtering — status: done

`MovieFilterInput` in `schema.graphqls`; filter controls on `/movies`; expanded
genre seed and TMDB manifest. See [catalogue.md](../plans/v2/catalogue.md)
(V2-05/V2-06/V2-07) and [verification/v2-acceptance.md](../verification/v2-acceptance.md).

1. THE Catalogue Service SHALL provide a seed dataset spanning multiple distinct Genre Codes.
2. Genre and release-year filters SHALL combine with AND semantics.
3. WHEN a user changes a filter value, THE Movie Listing SHALL reset the pagination offset to the first page.
4. WHEN a filter combination matches no movies, THE Movie Listing SHALL display an empty-result message.
5. THE Movie Listing SHALL report the total count of movies matching the active filter values.

### Requirement 4: MyDramaList-Style Cast and Credits Display — status: done

`CreditPersonRow`/`CreditSection` in `lib/features/credits/`. See
[frontend.md](../plans/v2/frontend.md) (V2-10) and
[verification/v2-acceptance.md](../verification/v2-acceptance.md). Batched
person hydration (no N+1 gRPC) already existed from v1.

1. WHERE a Movie Detail displays a Credit, THE Movie Detail SHALL display the person's photo on the left and name/role on the right.
2. CAST credits show character name as the role text; CREW credits show the credit role title.
3. IF a person has no photo, THEN a placeholder image is shown; IF a person is unavailable from People Service, THEN an unavailable-person indicator is shown in place of the name.
4. Cast Credits are ordered by billing order.

### Requirement 5: Movie Comment Section — status: in progress

Backend done (V2-13/V2-14 in [catalogue.md](../plans/v2/catalogue.md)):
`movie_comment` table, entity/repository/rules, and the `comments`/
`addMovieComment` GraphQL fields, with server-generated id/timestamp and
reverse-chronological paging. The Movie Detail UI (list + submit form,
criteria 1/2/4 below) is not yet built — no frontend task for it exists yet.

1. THE Movie Detail SHALL display a comment section listing existing Comments, each with author display name, text, and creation timestamp.
2. WHEN a user submits a Comment with non-empty text, THE Catalogue Service SHALL persist it with a server-generated timestamp and display it in the section.
3. IF text is empty/whitespace-only or exceeds the maximum length, THEN the submission is rejected with a validation message/error.
4. Comments are displayed in reverse chronological order; an empty state invites the first comment.

### Requirement 6: People List Photo Display — status: done

`Person.photoUrl` in `schema.graphqls`; `PersonListRow.svelte`. See
[frontend.md](../plans/v2/frontend.md) (V2-11) and
[verification/v2-acceptance.md](../verification/v2-acceptance.md).

1. WHERE the People Listing displays a person, THE People Listing SHALL display the person's photo (or placeholder) on the left and name on the right.
2. Existing pagination controls and displayed total count are retained.

### Requirement 7: Artwork Storage Migration to MinIO — status: done

`MinioArtworkStore` implements `ArtworkStore`; see [ADR-14](../decisions/0014-minio-object-storage.md) and [verification/v2-acceptance.md](../verification/v2-acceptance.md).

1. THE media module SHALL provide a MinIO-backed `ArtworkStore` implementation.
2. Storage Keys are server-generated (UUID + safe extension), never derived from the client filename.
3. WHERE MinIO is selected by configuration, both services SHALL use it in place of the local filesystem store.
4. Existing artwork upload validation and HTTP serving behavior SHALL be preserved.
