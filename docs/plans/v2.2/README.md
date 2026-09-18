# V2.2: input validation and error-message hardening

```yaml
status: current
canonical_for: v2.2-backlog
last_verified: 2026-09-17
```

Nothing in this file is implemented yet. It is the outcome of a code review of
the whole validation/error path (domain rules → gRPC → GraphQL → BFF → form
UI), prompted by two user-reported defects:

> "Please check the highlighted fields and try again." — but no field is
> highlighted, and the message never says what is wrong.

> `3/3/52452242` is accepted or fails generically; `3/4/2026` birth date with a
> `4/4/2026` death date is accepted.

The review found those are two symptoms of four systemic gaps: **(1)** the BFF
throws away the specific message the backend already produces, **(2)** no
component has ever rendered a field-level error, so "highlighted fields" is
copy describing a UI that does not exist, **(3)** dates are structurally
parsed but never semantically validated, and **(4)** free-text fields have no
character-class screening and two of them have no length bound at all.

Related: [plans/v2/release.md](../v2/release.md) (V2 release hardening, still
open and unaffected by this file) and [plans/v2.1/README.md](../v2.1/README.md)
(post-v2 UI fixes, done).

## Status

| Item | Area | Status |
|---|---|---|
| [V2.2-01](#v22-01-carry-the-specific-validation-message-to-the-user) | BFF + UI: stop discarding backend validation messages | ✅ done |
| [V2.2-02](#v22-02-render-real-field-level-errors) | UI: real field-level errors, so "highlighted" is true | ✅ done |
| [V2.2-03](#v22-03-date-semantics-for-movies-and-people) | Domain: release/birth/death date semantics | ✅ done |
| [V2.2-04](#v22-04-malformed-dates-and-numbers-are-bad_user_input-not-internal_error) | Boundary: malformed scalars → `BAD_USER_INPUT` | ✅ done |
| [V2.2-05](#v22-05-length-bounds-on-every-text-field-counted-consistently) | Domain: bound `synopsis`/`biography`, declare the counting unit | ✅ done |
| [V2.2-06](#v22-06-character-class-screening-on-text-fields) | Domain: control/NUL/bidi screening | ✅ done |
| [V2.2-07](#v22-07-malformed-ids-are-not-found-not-internal_error) | Boundary: malformed UUID → `NOT_FOUND` | ✅ done |
| [V2.2-08](#v22-08-fix-the-wrong-error-codes-and-http-statuses) | BFF: wrong codes/statuses on media, update, delete paths | ✅ done |
| [V2.2-09](#v22-09-client-side-mirrors-of-the-server-rules) | UI: `maxlength`/`min`/`max` mirrors, app error page | ✅ done |
| [V2.2-10](#v22-10-controlled-country-reference-for-place-of-birth) | Domain + UI: controlled country vocabulary for place of birth | ✅ done |
| [V2.2-11](#v22-11-concurrent-edit-handling) | BFF: narrow update masks, preserve input, staleness banner | ✅ done |
| [V2.2-12](#v22-12-character-counters-on-every-bounded-field) | UI: live character counters on every bounded field | ✅ done |
| [V2.2-13](#v22-13-seed-comments-in-the-importer) | Importer: seeded comments + a comment idempotency key | ✅ done |
| [V2.2-14](#v22-14-test-inventory-gaps) | Tests: extremity, invalid-character + emoji coverage | ✅ done |

---

## Code review findings

Severity: **A** = user-visible wrong/confusing behavior today, **B** = missing
guard that lets bad data persist, **C** = correctness/robustness nit.

| # | Sev | Finding | Evidence |
|---|---|---|---|
| F1 | A | `BAD_USER_INPUT` copy promises highlighted fields. No component anywhere sets `aria-invalid`, `aria-describedby`, or renders a per-field error — a repo-wide grep for those attributes returns zero hits in `.svelte` files. The only error surface is one page-level `StateBanner`. | [errors.ts:16](../../../frontend/src/lib/errors.ts) |
| F2 | A | The backend already produces a specific, curated, safe message (`"death_date must not be before birth_date"`, `"name must be at most 300 characters"`), the gRPC client and GraphQL resolver both preserve it, and `GraphQlRequestError` carries it into the BFF — then every form action calls `messageForCode(code)` and drops it on the floor. | [graphql.ts:46](../../../frontend/src/lib/server/graphql.ts), [GraphQlExceptionResolver.kt:42](../../../backend/catalogue-service/src/main/kotlin/com/moviecatalogue/catalogue/graphql/GraphQlExceptionResolver.kt), [PeopleGrpcClient.kt:132](../../../backend/catalogue-service/src/main/kotlin/com/moviecatalogue/catalogue/people/PeopleGrpcClient.kt), all eight `+page.server.ts` catch blocks |
| F3 | B | **`releaseDate` is never validated.** `MovieRules` has `validateReleaseYear` (1888–2100) but it is only applied to the *filter* input. `createMovie`/`updateMovie` write `command.releaseDate` straight through, so `0001-01-01` or `9999-12-31` persists. | [MovieUseCases.kt:88,121](../../../backend/catalogue-service/src/main/kotlin/com/moviecatalogue/catalogue/application/MovieUseCases.kt), [Rules.kt:52](../../../backend/catalogue-service/src/main/kotlin/com/moviecatalogue/catalogue/domain/Rules.kt) |
| F4 | B | Person dates are only checked for `death >= birth`. Both may be in the future, and either may be centuries before cinema existed. The user's own example (`3/4/2026` birth, `4/4/2026` death) **passes** the current rule — death *is* after birth — and is only wrong because both are in the future. | [PersonRules.kt:43](../../../backend/people-service/src/main/kotlin/com/moviecatalogue/people/domain/PersonRules.kt), [V1__init_people.sql:16](../../../backend/people-service/src/main/resources/db/migration/V1__init_people.sql) |
| F5 | A | A malformed date reaches the `Date` scalar's `parseValue`, which throws `CoercingParseValueException`. That happens during **variable coercion, before execution**, so `GraphQlExceptionResolver` never sees it and the error carries no `extensions.code`. `gql()` defaults to `INTERNAL_ERROR` → "Something went wrong on our end." for what is plainly user input. | [GraphQlScalarConfig.kt:41](../../../backend/catalogue-service/src/main/kotlin/com/moviecatalogue/catalogue/graphql/GraphQlScalarConfig.kt), [graphql.ts:45](../../../frontend/src/lib/server/graphql.ts) |
| F6 | B | `synopsis` and `biography` have **no length bound at any layer** — no domain rule, no DB `CHECK` (both are `TEXT`). A single request can store an arbitrarily large document. | [Rules.kt](../../../backend/catalogue-service/src/main/kotlin/com/moviecatalogue/catalogue/domain/Rules.kt), [PersonRules.kt](../../../backend/people-service/src/main/kotlin/com/moviecatalogue/people/domain/PersonRules.kt), [V1__init_catalogue.sql:9](../../../backend/catalogue-service/src/main/resources/db/migration/V1__init_catalogue.sql) |
| F7 | C | Length checks count UTF-16 code units (`String.length`) while the columns are `VARCHAR(n)` (code points), so the two units disagree for any non-BMP character. The app is always the stricter of the two, so nothing can overflow a column — but no layer states which unit it means and no UI shows a count, so a user pasting emoji into a comment sees input silently stop with no explanation. Resolved by [making UTF-16 the one declared unit](#text-length-and-how-it-is-counted), not by switching to code points. | `normalizeName`, `normalizeTitle`, `normalizeOptionalText`, `normalizeText` |
| F8 | B | **No character-class screening anywhere.** Nothing rejects C0/C1 control characters, `U+0000`, zero-width characters, or bidi overrides (`U+202E`) in any stored field. `U+0000` specifically is rejected by PostgreSQL itself (`22021`), which surfaces as a `DataAccessException` — not a `CatalogueException` — and therefore as `INTERNAL_ERROR`. Svelte escaping means this is a data-integrity and display-spoofing issue rather than XSS, but it is still user-reachable via a direct POST to the form action. | whole domain layer |
| F9 | A | A malformed UUID in a path or mutation argument throws `IllegalArgumentException` from `UUID.fromString`. The `runCatching` wrappers only translate `NotFoundException`, so everything else rethrows → `INTERNAL_ERROR` → `throwPageLoadError` renders **503 "A required service is temporarily unavailable"** for `/movies/not-a-uuid`, which should be a 404. | [MovieController.kt:29](../../../backend/catalogue-service/src/main/kotlin/com/moviecatalogue/catalogue/graphql/MovieController.kt), [QueryControllers.kt:21](../../../backend/catalogue-service/src/main/kotlin/com/moviecatalogue/catalogue/graphql/QueryControllers.kt), 10 sites in [MutationController.kt](../../../backend/catalogue-service/src/main/kotlin/com/moviecatalogue/catalogue/graphql/MutationController.kt) |
| F10 | A | Both media advices emit `code: "STORAGE_UNAVAILABLE"`, which is **not in the BFF's `ErrorCode` union**, so `messageForCode` falls through to the internal-error copy. A MinIO outage tells the user "Something went wrong on our end" instead of "temporarily unavailable, try again in a moment". | [ArtworkExceptionAdvice.kt:46](../../../backend/catalogue-service/src/main/kotlin/com/moviecatalogue/catalogue/artwork/ArtworkExceptionAdvice.kt), [PhotoExceptionAdvice.kt:43](../../../backend/people-service/src/main/kotlin/com/moviecatalogue/people/photo/PhotoExceptionAdvice.kt), [errors.ts:15](../../../frontend/src/lib/errors.ts) |
| F11 | A | Upload actions map **every** non-`PAYLOAD_TOO_LARGE` failure to HTTP 415: `fail(code === 'PAYLOAD_TOO_LARGE' ? 413 : 415, …)`. A storage outage or a deleted movie is reported as "unsupported media type". | [movies/[id]/edit/+page.server.ts:69](../../../frontend/src/routes/movies/[id]/edit/+page.server.ts), [people/[id]/edit/+page.server.ts:56](../../../frontend/src/routes/people/[id]/edit/+page.server.ts) |
| F12 | A | `update` actions map every non-validation failure to **409 Conflict**, including `DEPENDENCY_UNAVAILABLE` and `NOT_FOUND`. Delete-artwork/photo/credit actions map everything to **503**, including `NOT_FOUND`. | same two files |
| F13 | A | Submitting the upload form with no file returns the generic `BAD_USER_INPUT` copy — "check the highlighted fields" for what is simply "you didn't choose a file". | same two files, `uploadArtwork`/`uploadPhoto` guard |
| F14 | C | `runtimeMinutes: form.get('runtimeMinutes') ? Number(...) : null` yields `NaN` for non-numeric input, which `JSON.stringify` serializes as `null` — the value is **silently discarded** rather than reported. Reachable by a non-JS submit or a pasted value. | [movies/new/+page.server.ts:36](../../../frontend/src/routes/movies/new/+page.server.ts), [movies/[id]/edit/+page.server.ts:43](../../../frontend/src/routes/movies/[id]/edit/+page.server.ts) |
| F15 | C | `expectedVersion = Number(form.get('expectedVersion') ?? '0')` is `NaN` if the hidden field is missing or tampered; `NaN` hits the `Long` scalar and produces another uncoded coercion error → `INTERNAL_ERROR`. | both edit actions |
| F16 | C | The movie edit `update` action does not return `values` on failure, while the movie *create* action does. Any field-error rendering pass needs both to behave the same. | [movies/[id]/edit/+page.server.ts:53](../../../frontend/src/routes/movies/[id]/edit/+page.server.ts) |
| F17 | C | `CreditDialog.onQueryInput` has no `catch`: a failed or non-JSON response rejects inside the debounce timer (unhandled rejection), `suggestions` goes stale, and the user gets no feedback — only the spinner clears via `finally`. | [CreditDialog.svelte:64](../../../frontend/src/lib/components/CreditDialog.svelte) |
| F18 | C | No `+error.svelte` exists, so every `error(404)`/`error(503)` thrown by `throwPageLoadError` renders SvelteKit's unstyled fallback page, outside the app shell. | [frontend/src/routes/](../../../frontend/src/routes/) |
| F20 | B | `person.place_of_birth` is free-text `VARCHAR(300)` with no controlled vocabulary — exactly the state `movie.original_language` was in before [V2.1-06](../v2.1/README.md#v21-06-controlled-language-reference-table--searchable-dropdown-done). "USA", "U.S.A.", "United States" and "Untied States" are all accepted and are four different values to any future grouping or filter. | [V1__init_people.sql:11](../../../backend/people-service/src/main/resources/db/migration/V1__init_people.sql), [people/new/+page.svelte:53](../../../frontend/src/routes/people/new/+page.svelte) |
| F21 | A | **Every update sends a full field mask.** Both edit actions build `input` with all seven keys unconditionally, so `updateMovie` always masks every field. Two users editing *different* fields of the same movie therefore collide on the row `@Version` and one is told "changed by someone else" even though their edits were disjoint and mergeable. | [movies/[id]/edit/+page.server.ts:38](../../../frontend/src/routes/movies/[id]/edit/+page.server.ts), [people/[id]/edit/+page.server.ts:28](../../../frontend/src/routes/people/[id]/edit/+page.server.ts) |
| F22 | A | Conflict recovery discards the user's work. The action returns only `{ message, section }` — not `values` — and the copy says "Reload to get the latest version, then reapply your changes", i.e. retype everything from memory. Nothing shows *which* fields the other editor changed. | [errors.ts:18](../../../frontend/src/lib/errors.ts), both edit actions |
| F23 | C | `ConflictException("movie was modified concurrently (expected 3, is 5)")` puts internal version numbers into a user-facing message. Useful in a log, meaningless in a banner. | [MovieUseCases.kt:110](../../../backend/catalogue-service/src/main/kotlin/com/moviecatalogue/catalogue/application/MovieUseCases.kt), [PeopleApplicationService.kt:130](../../../backend/people-service/src/main/kotlin/com/moviecatalogue/people/application/PeopleApplicationService.kt) |
| F24 | B | No field anywhere shows a character count. The only two `maxlength` attributes in the codebase (comment author and text) make the browser **silently refuse** further input at the limit with no counter, no message, and no indication why — and they are the only fields where a user is likely to write enough to hit one. | [movies/[id]/+page.svelte:107,111](../../../frontend/src/routes/movies/[id]/+page.svelte) |
| F25 | B | `movie_comment` has **no idempotency key** — no `tmdb_*` analog, and comments cannot be individually deleted (v2 design decision 5). Any seeding of comments would therefore duplicate the whole set on every importer rerun, breaking the "import rerun produces the same counts" property the verification checklist claims. | [V4__add_movie_comment.sql](../../../backend/catalogue-service/src/main/resources/db/migration/V4__add_movie_comment.sql), [ports.ts](../../../demo/importer/src/ports.ts) |
| F19 | C | A search query over 100 characters returns `BAD_USER_INPUT` and the search box shows "Please check the highlighted fields and try again" — in a control that has no fields. | [SearchUseCases.kt:154](../../../backend/catalogue-service/src/main/kotlin/com/moviecatalogue/catalogue/application/SearchUseCases.kt), [api/search/+server.ts:19](../../../frontend/src/routes/api/search/+server.ts) |

### What the review found to be *sound* (no change proposed)

Recorded so a later reader does not re-audit them: SQL `LIKE` metacharacter
escaping on both search paths (`SearchPattern`, `PersonSearch`, both with
`ESCAPE '\'`); storage-key path-traversal rejection; image validation by magic
bytes + full decode rather than filename/Content-Type, with a real WebP decoder
on the classpath (`imageio-webp`); the domain-exception → `extensions.code`
mapping itself; `INTERNAL_ERROR` never leaking a cause or stack trace to the
client; `validateLifeDates` being re-run against the **merged** entity on a
partial field-mask update, so changing only `birthDate` still checks it against
the stored `deathDate`; and Svelte's default output escaping.

---

## Confirmed rules to adopt

Concrete constants and the exact user-facing wording. Every message names the
offending value and what is wrong with it.

### Dates

| Rule | Bound | Message |
|---|---|---|
| Release date lower | `1888-10-14` (*Roundhay Garden Scene*; matches the existing `RELEASE_YEAR_MIN = 1888`) | `Release date 1850-01-01 is before the first film was made (14 October 1888).` |
| Release date upper | `today + 10 years` (see below) | `Release date 2262-01-01 is more than 10 years away — further ahead than films are ever announced. Check the year.` |
| Birth date upper | `today − 2 years` | `Birth date 2026-04-04 is in the future.` / `Birth date 2025-10-01 is less than 2 years ago — check the year.` |
| Birth date lower | `1850-01-01` | `Birth date 1600-01-01 is before 1850 — check the year.` |
| Death date upper | `today` | `Death date 2026-04-04 is in the future.` |
| Death vs birth | `death >= birth` | `Death date 2024-04-04 is before the birth date 2026-04-05.` |
| Lifespan sanity | `death − birth <= 130 years` | `Death date 2400-01-01 is 374 years after the birth date — check both.` |
| Malformed string | strict `YYYY-MM-DD` | `"3/3/52452242" is not a valid date. Use the date picker, or type it as YYYY-MM-DD.` |

Deliberately **future-permissive for movies and not for people**: a movie can
legitimately be a "coming soon" entry, a person cannot be born or die in the
future.

**Where the 10 years comes from.** The bound should be the longest lead time a
real film is ever announced at, so no legitimate entry is rejected. Ordinary
studio date-staking runs 3–5 years out (Marvel and Star Wars slate
announcements park untitled films in dated slots about that far ahead). The
known outlier is the *Avatar* sequels, dated roughly 9–12 years ahead of their
announced releases. Ten years covers that outlier with room to spare while
still catching every realistic typo, which is never marginally wrong — a
mistyped year lands decades or millennia out (`2262`, `5202`, `20262`), not
eleven years out. A film announced further ahead than this is a data-entry
mistake far more often than it is real.

This replaces the 5-year figure in the first draft of this plan; 5 years would
have rejected genuine *Avatar*-style entries.

`today` must come from an injected `java.time.Clock` (a `@Bean` per service),
not `LocalDate.now()`, so the boundary cases are testable without freezing real
time.

### "The youngest actor is at least a few years old"

Split into two, because they are not the same rule:

1. **Adopted (hard rule, People service):** the `today − 2 years` birth-date
   bound above. The justification is production lead time, not just typo
   catching: casting happens well before a release, and shooting,
   post-production and distribution add a year or more on top, so nobody
   entering a catalogue can have been born in the last two years and already
   be creditable. Infant roles do not weaken this - they are typically
   uncredited, or shared between twins, or the character is credited to the
   older actor who plays them later; an infant on set is closer to a prop than
   a credited performer. Two years is therefore conservative rather than
   aggressive.
2. **Adopted (on the credit):** a person who was not yet born at a movie's
   release cannot be credited on it - an **error**, checked at submit time.
   The under-3 advisory once proposed alongside it is **deferred**: an actor
   really can have been 1 at release on an old film. Detailed in
   [V2.2-03b](#v22-03b-credit-age-rules).

   This reverses the first draft of this plan, which proposed advisory-only on
   the grounds that the check would need an extra cross-service fetch. **That
   objection turned out to be wrong.** `CreditUseCases.addCredit` already
   fetches the full person over gRPC at step 2 - before any insert,
   specifically to validate the person reference - so `person.birthDate` is
   already in hand alongside the movie. The check costs no additional call and
   introduces no new failure mode: the credit path already fails with
   `DEPENDENCY_UNAVAILABLE` when People is down.

### Text length, and how it is counted

**One unit, declared, used at every layer: the UTF-16 code unit.** This
reverses the first draft of this plan, which proposed switching to code points.

| Layer | Counts | Consequence |
|---|---|---|
| `<input maxlength>` / JS `.length` | UTF-16 code units | fixed by the HTML spec — a native `maxlength` **cannot** express a code-point limit |
| Kotlin `String.length` | UTF-16 code units | what every rule already uses |
| PostgreSQL `VARCHAR(n)` / `length()` | code points | 😀 is 1 here, 2 in the layers above |

Because a UTF-16 count is always **greater than or equal to** the code-point
count, a value that passes an *n*-unit app rule always fits an *n*-code-point
column. Choosing UTF-16 therefore makes the app the stricter layer at every
boundary, and — the point that matters — the number the server enforces, the
number `maxlength` enforces, and the number the counter shows are all the
**same number**, with no conversion anywhere to drift.

The cost is stated honestly: an emoji counts as 2 toward the limit. That is the
behavior asked for — *"if a character is to be taken as 2 characters, then it
is 2 characters"* — and the counter is what makes it fair, because the user can
see it happening instead of watching input stop for no reason.

The database `VARCHAR(n)`/`CHECK` bounds stay as they are and remain
**deliberately looser** than the app rule. That is not an inconsistency to
clean up later: it is the backstop sitting outside the stricter rule. A comment
of the alphabet is the full 2 000 in both units; a comment of 1 000 emoji is
2 000 units and 1 000 code points, rejected by the app and accepted by the
column. Never the reverse.

| Field | Current | Proposed | Emoji | Note |
|---|---|---|---|---|
| `movie.title` / `originalTitle` | 300 | 300 | ✗ | |
| `movie.synopsis` | **unbounded** | 5 000 | ✗ | new; add a DB `CHECK` as backstop |
| `person.name` | 300 | 300 | ✗ | |
| `person.biography` | **unbounded** | 5 000 | ✗ | new; add a DB `CHECK` as backstop |
| `person.placeOfBirth` | 300 | 300 | ✗ | |
| `credit.characterName` | 300 | 300 | ✗ | |
| `credit.sourceRoleName` | 150 | 150 | ✗ | |
| `comment.authorDisplayName` | 50 | 50 | ✓ | user-authored, not catalogue data |
| `comment.text` | 2 000 | 2 000 | ✓ | user-authored, not catalogue data |
| search / autocomplete query | 100 | 100 | — | clamp client-side instead of erroring (F19) |

Message form: `Synopsis must be 5,000 characters or fewer — you entered 12,480.`

Every one of these fields gets a live counter in the UI
([V2.2-12](#v22-12-character-counters-on-every-bounded-field)), not just the
two that have a `maxlength` today.

### Emoji

**Allowed in user-authored content, rejected in catalogue metadata.** The line
is who the text belongs to, not how long it is: a comment is a person speaking,
a title/name/synopsis is a catalogue record that has a correct value.

- **Allowed:** `comment.text` and `comment.authorDisplayName`. Comments are
  unauthenticated, unmoderated, user-written prose (v2 design decision 4) —
  emoji are normal in that register, and an author display name is a
  self-chosen handle rather than a person's real name. *(Allowing them in the
  display name as well as the text is confirmed, subject to the same screening
  as the comment body - emoji are permitted, but the control-character,
  bidi-override, zero-width and newline rules all still apply, and the
  adjacency-scoped `U+200D` rule above applies unchanged. A display name is
  still a single-line field: newlines stay rejected.)*
- **Rejected:** `title`, `originalTitle`, `synopsis`, `name`, `biography`,
  `placeOfBirth`, `characterName`, `sourceRoleName` — message
  `Title can't contain emoji.`

**Can PostgreSQL store them? Yes — with one thing to pin down.** The catalogue
and people databases run `postgres:16-alpine`, whose default `initdb` produces
a **UTF8**-encoded database, and the PostgreSQL JDBC driver always transmits
text as UTF-8. A non-BMP emoji is a 4-byte UTF-8 sequence, which UTF8 encoding
handles natively; it counts as **one** character toward `VARCHAR(n)` and
`length()`. Nothing in the stack needs changing, and JSON (GraphQL) and
protobuf `string` (gRPC) are both UTF-8 end to end.

What is *not* currently pinned is the encoding itself — it is inherited from
the image default rather than declared. This plan adds
`POSTGRES_INITDB_ARGS: "--encoding=UTF8 --locale=C.UTF-8"` to both database
services in `compose.yaml` so a differently-configured host cannot silently
initialise a `SQL_ASCII` database, plus an integration test that round-trips a
multi-code-point emoji (a ZWJ sequence such as 👨‍👩‍👧‍👦, not just 😀) through
create → read. Assume nothing here; assert it.

**Defining "emoji" in code.** The backend runs JDK 21, which added
`Character.isEmoji(int)`, `isEmojiPresentation`, `isEmojiModifier`,
`isEmojiComponent` and `isExtendedPictographic` — so the check is a standard
library call, not a hand-maintained codepoint table. The rejection screen uses
`isExtendedPictographic` plus the emoji components (skin-tone modifiers
`U+1F3FB`–`U+1F3FF`, regional indicators, `U+FE0F`), so flag sequences and
skin-tone variants are caught as well as bare pictographs. The frontend mirror
uses `/\p{Extended_Pictographic}|\p{Emoji_Modifier}|\p{Regional_Indicator}/u`.
The two must agree; a shared test vector list keeps them honest.

`U+FE0F` (variation selector-16) is treated as an emoji component in the
metadata fields and allowed in comments — it is what turns `☂` into `☂️`, and
rejecting the base character but not the selector would be incoherent.

### Character classes

A shared `TextRules` screen applied to every stored text field, before the
length check:

| Rejected | Why | Message |
|---|---|---|
| `U+0000` | PostgreSQL rejects it outright (`22021`), today as an `INTERNAL_ERROR` | `Name contains a null character, which can't be stored.` |
| C0/C1 control characters | invisible, corrupts display and exports | `Name contains a control character at position 12. Remove it and try again.` |
| bidi overrides `U+202A`–`U+202E`, `U+2066`–`U+2069` | display spoofing — text renders differently from what is stored | `Name contains a text-direction override character, which isn't allowed.` |
| zero-width `U+200B`–`U+200F`, `U+FEFF` | invisible padding defeats uniqueness/search | `Name contains an invisible character, which isn't allowed.` |

Exceptions: `\n`, `\r`, `\t` are **allowed** in the long-text fields
(`synopsis`, `biography`, `comment.text`) and **rejected** in single-line fields
(`title`, `name`, `placeOfBirth`, `characterName`, `authorDisplayName`), where a
newline can only be paste damage.

**One carve-out, and it is the one most likely to be got wrong.** `U+200D`
(zero-width joiner) falls inside the `U+200B`-`U+200F` zero-width rule above,
but it is the glue in every emoji ZWJ sequence - a four-person family emoji is
four pictographs joined by three ZWJs. Rejecting it would allow emoji in
comments in name only while breaking every multi-person, profession and flag
emoji. `U+200D` is therefore allowed in the two comment fields **only between two
emoji code points** - not as a free-floating character. Allowing it anywhere in
those fields would reopen the invisible-character hole the zero-width rule
exists to close (a joiner slipped between ordinary letters renders as nothing
but defeats exact matching). Adjacency-scoped, it permits every real emoji
sequence and nothing else. It stays rejected outright everywhere else, where
the pictographs themselves are rejected anyway. This has its own test, in both
directions.

Input is NFC-normalized before screening so a
decomposed and a precomposed "é" behave identically; NFC leaves emoji sequences untouched.

Explicitly **not** doing: an HTML/script-tag blocklist. Svelte escapes output;
a blocklist would reject legitimate titles (`<Dollars>`, `Se7en`) and provide no
real protection. The defense is escaping at render, which already holds.

`TextRules` is duplicated in each service's `domain` package rather than placed
in a shared module — matching the existing deliberate duplication of
`SearchPattern`/`PersonSearch` and `UuidV7`, which keeps `:media` about media
and preserves the service boundary
([ADR-1](../../decisions/0001-service-split-by-data-ownership.md)). The two
copies are kept identical by a shared test vector list, not by shared code.

### Injection: what was checked, and what is actually missing

Allowing emoji into comment fields raised the question of whether those fields
widen the injection surface. They do not - emoji are ordinary characters, not
metacharacters in any language in this stack - but the surrounding defenses
were audited rather than assumed. Results, verified by inspection:

| Vector | Status | Evidence |
|---|---|---|
| SQL injection | **Clean** | every repository query is JPQL with named parameters (`:pattern`); no `nativeQuery`, no `createQuery`, no string-built SQL anywhere in either service |
| SQL `LIKE` metacharacters | **Clean** | `SearchPattern`/`PersonSearch` escape `%`, `_`, `\` and the queries declare `ESCAPE ''` |
| Stored XSS | **Clean** | zero occurrences of `{@html}` in any `.svelte` file, and zero of `innerHTML`, `outerHTML`, `eval(`, `new Function` or `document.write` in frontend source - so every interpolated value goes through Svelte's default escaping |
| Response header injection | **Clean** | the media proxy copies a fixed allowlist of five header names, never user-controlled ones |
| Control characters / NUL | **Being fixed** | [V2.2-06](#v22-06-character-class-screening-on-text-fields) |
| Invisible / bidi spoofing | **Being fixed** | same, including the adjacency-scoped ZWJ rule |
| **Content-Security-Policy** | **Missing** | see below |

**The one real gap.** The Catalogue sets `X-Content-Type-Options`,
`X-Frame-Options`, `Referrer-Policy` and `Cross-Origin-Resource-Policy` - but
the Catalogue is not the public entry point. The **BFF** is, and it is what
serves HTML to browsers, and `frontend/svelte.config.js` configures no `csp`
block and there is no `hooks.server.ts` adding one. So the application has no
Content-Security-Policy at all on the surface where one matters.

That is not currently exploitable, because the escaping above holds. It is
exactly the defense-in-depth layer you want *underneath* escaping, for the day
someone adds an `{@html}` or a third-party script. SvelteKit has first-class
support: a `kit.csp` block with `mode: 'auto'` emits nonces for its own inline
scripts, so this is configuration rather than a build.

**Sequenced after this plan, not inside it.** The CSP and its supporting tests
are tracked separately in [plans/v2.3/README.md](../v2.3/README.md), to be
started once v2.2 is implemented, tested and verified. The audit above stays
here because it is what justifies letting emoji into comment fields; the
remediation is its own slice. Nothing in v2.2 depends on it.

**Explicitly decided against:** homoglyph/confusable-script detection on
comment author names. Comments are unauthenticated, so a display name carries
no authority and impersonation is already trivial - anyone can simply type the
same name. Mixed-script detection would add real complexity and false
positives (legitimate names mix scripts) to defend an identity that does not
exist. Revisit only if comments ever gain authentication.

**Noted, not yet actionable:** CSV formula injection (a field beginning `=`,
`+`, `-` or `@` becoming a live formula when opened in a spreadsheet) is not a
risk today because nothing exports data. If an export is ever added, that
escaping belongs in the exporter, not in the input rules - rejecting a title
that starts with a hyphen would be wrong.

### Concurrent edits: what to do about `version`

Asked directly: the user never sees or sets `expectedVersion`, so what is the
right behavior when two people edit the same record at once?

**Keep optimistic locking. The mechanism is already right** - a `@Version`
column, compared server-side, rejecting the stale writer - and it is the
standard answer for a stateless web form. The alternative, pessimistic locking
(check a record out, hold a lock), is the wrong trade here: it needs lock
expiry, a release path for abandoned tabs and closed laptops, and an
administrative unlock, and it blocks a second person who only wants to fix a
typo in a different field. None of that is worth it for a catalogue with no
authentication.

**What is wrong is everything around the locking.** Three defects, in the order
they bite:

1. **A conflict is reported where none exists** (F21). The BFF sends all seven
   fields on every update, so the mask is always full and any concurrent edit
   collides - even when one person changed the runtime and the other changed
   the synopsis. *Fix:* send only the fields whose value actually differs from
   the one loaded into the form. The mutation is already field-masked and
   `MutationController` already derives the mask from the argument map's
   present keys, so this is a BFF-side change with no schema impact. Most
   everyday "conflicts" disappear at this step alone.
2. **The user's work is thrown away** (F22). *Fix:* return `values` on conflict,
   exactly as the create actions already do, and re-render the form with what
   they typed. Never make someone retype from memory.
3. **"Reload and reapply your changes" says nothing about what changed.**
   *Fix (scaled down):* say something specific and honest instead - *"Someone
   else changed this movie while you were editing. What you entered is still
   here; reload to see their version."* Combined with fix 2, the user keeps
   their work and knows exactly what happened.

**Scoped down from the first draft.** That draft proposed a three-way merge
with per-field "keep mine / use theirs" resolution. Dropped: genuine
same-field collisions are rare, fixes 1 and 2 remove nearly all of the pain,
and a per-field conflict UI is a large build defending an uncommon case. The
merge machinery can be added later without rework if conflicts ever prove
frequent - fixes 1 and 2 are prerequisites for it either way, not detours.

### Would an edit lock be better?

Proposed: block the edit page when someone else is already on it - *"This page
is currently being edited."* Direct answer: **no, and the reason is the one
already spotted** - it does not solve simultaneous entry, so the `@Version`
check has to stay underneath it regardless. That makes a lock purely additive
complexity on top of the mechanism that actually works, not a replacement for
it. Four more problems, each concrete rather than theoretical:

- **There is no user identity to lock against.** The app has no
  authentication, so a lock can only key on a browser session. Two tabs
  belonging to the same person lock each other out.
- **Releasing the lock is the hard part.** A closed laptop, a crashed tab or
  simply navigating away leaves the record locked. Handling that needs a
  heartbeat from the edit page, a TTL, and a "take over anyway" override - and
  the override reintroduces exactly the concurrent-write race the lock was
  meant to prevent.
- **It needs shared state the project does not have.** Locks must be visible
  across BFF instances, so they need Redis or a lock table.
  [ADR-9](../../decisions/0009-defer-search-cache-messaging.md) explicitly
  deferred caching and messaging; this would reverse that for a rare problem.
- **It blocks legitimate work.** One person who opens the editor and wanders
  off stops everyone else for the whole TTL.

**But the instinct behind it is right:** warn me early, not after I have done
the work. That is worth having, and it is cheap without any locking - a
**non-blocking staleness check**. The edit page already holds the record's
`version`. Re-check it on window focus and just before submit; if the server's
version has moved, show a dismissible banner: *"This movie was changed by
someone else since you opened it. Reload to get the latest."* That delivers the
early warning a lock promises, needs no shared store, no heartbeat, no TTL and
no override, blocks nobody, and composes with fixes 1 and 2 instead of
duplicating them. Included as step 4 below.

**Also:** keep version numbers out of user-facing copy (F23) - `expected 3, is
5` belongs in the log - and reject a non-integer `expectedVersion` as user
input instead of sending `NaN`
([V2.2-04](#v22-04-malformed-dates-and-numbers-are-bad_user_input-not-internal_error), F15).

**Not proposed:** auto-merging the genuine-conflict row without asking, live
presence indicators ("Alice is editing this"), or operational-transform
collaborative editing. Each needs infrastructure this app does not have, and
the three fixes above remove nearly all of the pain without any of it.

---

## Implementation plan

### V2.2-01: carry the specific validation message to the user

The backend message for `BAD_USER_INPUT` is already curated: only
`ValidationException.message` is surfaced, and every such message is written by
this codebase from field names and bounds — it never contains a stack trace,
a SQL fragment, a cause, or user PII beyond the value the user just typed.
`INTERNAL_ERROR` keeps its opaque copy and logs the real cause server-side.
That is what makes passing the message through safe, and the test plan asserts
it stays that way.

1. Add an optional `field` to `ValidationException` in both services
   (`ValidationException(message, field = null)`), and populate it at each
   throw site (`"title"`, `"synopsis"`, `"birthDate"`, `"deathDate"`,
   `"releaseDate"`, `"runtimeMinutes"`, `"characterName"`, …). The *GraphQL*
   field name is used, not the DB column, so the BFF can key straight to a
   form input.
2. `GraphQlExceptionResolver` adds `"field" to ex.field` to `extensions`
   alongside `code`.
3. People → Catalogue: carry the field over gRPC in a `Metadata` key
   (`x-field`, ASCII) set in `PeopleGrpcService.toStatus()` and read in
   `PeopleGrpcClient.toDomain()`, rather than parsing it back out of the
   description string.
4. `GraphQlRequestError` gains a `field?: string`; `gql()` reads
   `extensions.field`.
5. `errors.ts` gains `messageForValidation(code, serverMessage)`: returns the
   server message for `BAD_USER_INPUT` when one is present, and the existing
   generic copy otherwise. `BAD_USER_INPUT`'s generic copy is reworded to
   `'Please correct the highlighted field and try again.'` and becomes the
   fallback only.
6. Every form action returns `{ message, fieldErrors, values }`;
   `codeForError` grows a sibling `fieldErrorForError(e)`.

### V2.2-02: render real field-level errors

1. New `lib/components/FieldError.svelte` and a `field-error` style in
   `app.css` (error text + a red left border / ring on the associated control),
   both honoring the existing token set.
2. Every form field wires `aria-invalid={!!fieldErrors.x}` and
   `aria-describedby="x-error"`, and renders `<FieldError id="x-error">` below
   the control. This is what makes the existing copy truthful.
3. On a failed submit, move focus to the first invalid control and keep the
   page-level `StateBanner` as the summary.
4. Applies to: movie new/edit, person new/edit, `CreditDialog`, the comment
   form, and both upload controls.

### V2.2-03: date semantics for movies and people

1. `MovieRules.validateReleaseDate(date, clock)` with the bounds above; call it
   from `createMovie` and from `updateMovie` under `maskReleaseDate`.
2. `PersonRules.validateBirthDate` / `validateDeathDate` / extend
   `validateLifeDates` with the future, floor, and lifespan checks; keep the
   existing merged-entity call site so partial updates stay correct.
3. Reword the existing `death_date must not be before birth_date` to the
   value-naming form in the table above.
4. Inject `Clock` as a `@Bean` in both services; tests use `Clock.fixed`.
5. DB: the existing `CHECK (death_date >= birth_date)` stays as the backstop.
   Do **not** add date-range `CHECK`s — "not in the future" is not expressible
   as an immutable constraint, and a split rule (half in SQL, half in Kotlin)
   is worse than one authoritative place.

#### V2.2-03b: credit age rules

| Condition | Treatment | Message |
|---|---|---|
| `birthDate > movie.releaseDate` | **Error** (`BAD_USER_INPUT`) | `Can't save: Jane Doe was born on 2026-04-04, after this movie's release on 2025-11-20. Check the dates and try again.` |
| age at release `< 3` | **Deferred** - see below | - |
| either date absent | no check | - |

**"Not yet born" is the whole rule for now. The under-3 advisory is deferred**,
because an actor genuinely can have been 1 year old at release - a 1965 film
can legitimately credit someone born in 1964 who is 60 today. Age at release
being low is not evidence of an error, so the advisory would fire on correct
historical data, and a warning that is usually wrong trains people to ignore
warnings.

This does not contradict the `today - 2 years` birth-date floor. That rule is
about **now** (nobody born in the last two years is in a catalogue yet); this
one is about **the past**, where an age of zero is unusual but real. Different
claims, both true.

**Where it runs, and when.** Two paths:

- **Adding a credit** - in `CreditUseCases.addCredit`, immediately after the
  existing `peopleClient.getPerson` call at step 2. Both values are already
  loaded: the movie was checked at step 1, the person was just fetched.
- **Editing a movie's release date** - in `updateMovie` when `releaseDate` is
  in the mask, checking the movie's existing credits. The Catalogue owns
  credits and `PersonHydrator` already does a batched person fetch, so it is
  one batched call.

**Validation happens on submit, not while typing.** The user edits the release
date freely; the check runs when they save, and a violation is a normal
validation error like any other - it names the offending person and both dates,
and the field is highlighted per
[V2.2-02](#v22-02-render-real-field-level-errors). Where several credited
people are affected, name up to three and summarise the rest
(`...and 4 others`), rather than reporting them one failed save at a time.

**If People is unreachable at submit time, the save is refused** with the
ordinary `DEPENDENCY_UNAVAILABLE` copy ("a required service is temporarily
unavailable - try again in a moment"), not saved-without-checking. This
reverses the "fail open" recommendation in the previous draft. `addCredit`
already behaves exactly this way when People is down - it is where
`DEPENDENCY_UNAVAILABLE` on the credit path comes from - so refusing here is
the consistent choice rather than a new restriction, and a People outage is
transient. ("Degraded"/"unavailable" here means the People gRPC call fails or
times out: `UNAVAILABLE`/`DEADLINE_EXCEEDED`, which the Catalogue maps to
`DependencyUnavailableException`. V2 made *read* paths survive that - a movie
page still renders, with credited people marked unavailable - but *writes* that
depend on People have always refused, and this is a write.)

**Person birth date edited** - *not* enforced, deliberately. Credits live in
the Catalogue and People does not know they exist; checking there would invert
the service dependency that
[ADR-1](../../decisions/0001-service-split-by-data-ownership.md) sets up. A
user who insists can still create the contradiction from that direction. That
is an accepted limit of a plausibility rule, not a hole in a constraint.

### V2.2-04: malformed dates and numbers are `BAD_USER_INPUT`, not `INTERNAL_ERROR`

1. **Backend:** add a `WebGraphQlInterceptor` that post-processes the response's
   errors and stamps `extensions.code = "BAD_USER_INPUT"` on pre-execution
   errors (`ErrorType.ValidationError`, `InvalidSyntax`) that carry no code —
   this is the only hook that sees variable-coercion failures, which happen
   before any `DataFetcherExceptionResolver` runs. The scalar's own message
   already reads `invalid Date '3/3/52452242'; expected ISO yyyy-MM-dd`.
2. **BFF:** validate date strings in the form action *before* building the
   GraphQL variables — strict `^\d{4}-\d{2}-\d{2}$` plus a real calendar-date
   check (rejects `2026-02-30`) — and produce the quoted-value message from the
   table. This gives the better message and keeps a malformed value from
   costing a network round trip; the backend guard remains the authority.
3. **BFF:** same treatment for `runtimeMinutes` (F14) and `expectedVersion`
   (F15) — reject a non-integer instead of sending `NaN`.

### V2.2-05: length bounds on every text field, counted consistently

1. Add `MovieRules.SYNOPSIS_MAX = 5000` and `PersonRules.BIOGRAPHY_MAX = 5000`;
   route `synopsis`/`biography` through `normalizeOptionalText`-style checks
   instead of a bare `.trim()`.
2. **Keep** `String.length` (UTF-16 code units) as the counting unit and say so
   in a comment at each rule, so nobody "fixes" it to code points later. Put the
   actual count in the message.
3. Migrations `V6__bound_synopsis_length.sql` (catalogue) and
   `V2__bound_biography_length.sql` (people) add
   `CHECK (char_length(...) <= 5000)` as a backstop. Note in the migration that
   `char_length` counts code points and is therefore **deliberately looser**
   than the app rule - it is the outer fence, not a mirror. This is the one
   place `Rules.kt`'s "DB constraints mirror the domain rules" comment needs
   qualifying rather than honoring literally.

### V2.2-06: character-class screening on text fields

Implement `TextRules` (NFC normalize → screen → trim → length) per the tables
above, in both services' `domain` packages, and route every text field through
it. The `U+0000` case specifically converts a current `INTERNAL_ERROR` into a
clear `BAD_USER_INPUT`.

The screen takes a per-field policy rather than being one function: `allowEmoji`
(comment fields only), `allowNewlines` (long-text fields only), and `allowZwj`
(follows `allowEmoji`). Emoji rejection uses JDK 21's `Character.isEmoji*` /
`isExtendedPictographic`; the frontend mirror uses `\p{Extended_Pictographic}`.
Both sides run the same shared vector list so they cannot drift.

Add `POSTGRES_INITDB_ARGS: "--encoding=UTF8 --locale=C.UTF-8"` to both database
services in `compose.yaml`, pinning the encoding that makes emoji storable
instead of inheriting it from the image default.

### V2.2-07: malformed IDs are `NOT_FOUND`, not `INTERNAL_ERROR`

Add a `parseId(raw): UUID` helper in the catalogue GraphQL package that throws
`NotFoundException("movie 'abc' not found")` on `IllegalArgumentException`
(mirroring `PeopleGrpcService.parseId`, which already does exactly this), and
use it at all 14 `UUID.fromString` call sites in the GraphQL layer. `/movies/
not-a-uuid` then renders the app's 404 rather than a 503.

### V2.2-08: fix the wrong error codes and HTTP statuses

1. Add `STORAGE_UNAVAILABLE` to the BFF `ErrorCode` union, mapped to the
   dependency-unavailable copy (F10).
2. Replace the two-way upload ternary with a code→status map:
   `PAYLOAD_TOO_LARGE→413`, `UNSUPPORTED_MEDIA_TYPE→415`, `NOT_FOUND→404`,
   `STORAGE_UNAVAILABLE`/`DEPENDENCY_UNAVAILABLE`→503, `BAD_USER_INPUT`→400,
   else 500 (F11). Share one `statusForCode` helper across all eight actions so
   `update` stops returning 409 for outages and the delete paths stop returning
   503 for not-found (F12).
3. Specific copy for the empty-file guard: `Choose an image file first.` (F13).
4. Return `values` from the movie edit `update` action (F16).
5. `catch` in `CreditDialog.onQueryInput`, surfacing an inline
   "Couldn't search people just now." (F17).
6. Clamp the search query client-side at 100 characters instead of letting the
   backend reject it (F19).

### V2.2-09: client-side mirrors of the server rules

Progressive enhancement only — the server stays the authority, and the tests
assert the server still rejects when the client guard is bypassed.

1. `maxlength` on title, originalTitle, synopsis, name, biography,
   placeOfBirth, characterName - matching the server bound exactly, which works
   because both count UTF-16 units. Paired with a visible counter
   ([V2.2-12](#v22-12-character-counters-on-every-bounded-field)); a `maxlength`
   without one is the silent-truncation trap of F24.
2. `min`/`max` on the three date inputs, computed from the same constants:
   release `1888-10-14` … `today+10y`; birth `1850-01-01` … `today−2y`; death
   `1850-01-01` … `today`. `DateField` gains `min`/`max` props.
3. Add `frontend/src/routes/+error.svelte` so 404/503 render inside the app
   shell with the message from `error(...)` (F18).

### V2.2-10: controlled country reference for place of birth

Mirrors [V2.1-06](../v2.1/README.md#v21-06-controlled-language-reference-table--searchable-dropdown-done)
for `person.place_of_birth`, which has the same defect for the same reason
(F20): a free-text field whose only guard is a length check.

**One structural difference from V2.1-06, and it matters.** `genre_code` and
`language_code` live in the **catalogue** database because the columns they
constrain (`movie.*`) live there. `person.place_of_birth` is owned by the
**People** service ([ADR-1](../../decisions/0001-service-split-by-data-ownership.md)),
so the `country_code` table belongs in the People database, and the frontend
dropdown needs the list to travel People → gRPC → Catalogue GraphQL → BFF
rather than being answered from a local table. That means a new
`ListCountries` RPC in [people.proto](../../../backend/contracts/src/main/proto/catalogue/people/v1/people.proto)
and a new `countryCodes` GraphQL query on the catalogue — work `languageCodes`
did not need. Do not copy V2.1-06's shape without accounting for this.

**Field shape.** TMDB's `place_of_birth` is a full string
(`"Honolulu, Hawaii, USA"`), so a country dropdown alone would discard the
city. Two options:

- **(A)** Replace `place_of_birth` with `birth_country_code` only. Simplest;
  loses city/region permanently.
- **(B) — recommended.** Split the field: keep `place_of_birth` as free text
  for the city/region part (still length- and character-screened per
  [V2.2-05](#v22-05-length-bounds-on-every-text-field-counted-consistently)/[V2.2-06](#v22-06-character-class-screening-on-text-fields)),
  and add a controlled `birth_country_code`. The UI shows a free-text "City or
  region" input beside a searchable "Country" dropdown, and the detail page
  renders them as `"Honolulu, Hawaii — United States"`.

The usual cost of (B) — backfilling an existing free-text column — is close to
zero here: the importer writes `placeOfBirth: null` for every imported person
([importer.ts:79](../../../demo/importer/src/importer.ts)), so seeded data has
nothing to parse. The migration only has to handle hand-entered values, and can
do what `V5__add_language_reference_data.sql` did — null out anything that
doesn't map, as a safety net rather than an expected code path.

Steps, assuming (B):

1. **Database (people):** `V3__add_country_reference_data.sql` — a
   `country_code` table (`code`/`name`/`active`/`display_order`, the same shape
   as `genre_code`/`language_code`, `CHECK (code ~ '^[A-Z]{2}$')`) seeded with
   the ISO 3166-1 alpha-2 set; add a nullable `birth_country_code` column on
   `person` with an FK to it. Codes are alpha-2 to match TMDB's
   `origin_country`/`iso_3166_1` fields, so a future importer change needs no
   translation table.
2. **Contracts:** `optional string birth_country_code` on the three person
   messages, a `birth_country_code` field-mask path, and a
   `ListCountries(ListCountriesRequest) returns (ListCountriesResponse)` RPC.
   Additive and backward-compatible — no field renumbering.
3. **People service:** a `CountryCode` entity + repository; validate a supplied
   code exists and is active before writing (the same shape as
   `CreditRules.requireActiveCode`, so a retired or unknown code is
   `INVALID_ARGUMENT` → `BAD_USER_INPUT` and never reaches the FK); add
   `birth_country_code` to `allowedMaskPaths` in
   `PeopleApplicationService.updatePerson`.
4. **Catalogue:** pass the field through `PeopleGrpcClient`/`PersonUseCases`;
   add `countryCodes(activeOnly: Boolean = true): [CountryCode!]!` and a
   resolved `Person.birthCountry: CountryCode` field so the frontend gets the
   display name without a second round trip — matching what `Movie.language`
   does today.
5. **Frontend:** generalize `LanguageSelect.svelte` into a shared
   `CodeCombobox.svelte` (identical behavior: client-side filtering over an
   already-loaded list, hidden input carrying the code, typing invalidates a
   stale selection, blur clears unmatched text) and have both `LanguageSelect`
   and a new `CountrySelect` use it, rather than copy-pasting a second
   combobox. The person `new`/`edit` `load` functions fetch `listCountries()`.
6. **Importer:** optionally parse TMDB's trailing country token into
   `birthCountryCode` — out of scope for this item unless the seed data starts
   carrying places at all.

New tests: country-code validation rejects unknown/inactive and accepts active
(unit); the FK rejects an unknown code at the DB level and the seeded set is
readable (integration); `countryCodes` query and `Person.birthCountry`
resolution (GraphQL integration); `ListCountries` contract (gRPC); and
`CodeCombobox.test.ts` inherits `LanguageSelect.test.ts`'s cases, run against
both the language and country instances.

### V2.2-11: concurrent-edit handling

Implements the three fixes argued in
[Concurrent edits](#concurrent-edits-what-to-do-about-version). Optimistic
locking stays; nothing about `@Version` or the schema changes.

1. **Narrow the mask.** Both edit pages already render the loaded record, so
   put the loaded value in a hidden `base.<field>` input and have the action
   include a field in `input` only when `submitted !== base`. An empty diff
   short-circuits to "no changes" without a mutation at all. This is the change
   that removes most false conflicts (F21).
2. **Preserve input on conflict.** Return `values` from both `update` actions
   (F22, and F16, which is the same omission).
3. **Clean the copy.** Drop version numbers from `ConflictException` messages
   (F23); keep them in the log line. Reword the `CONFLICT` copy to say the
   user's entries are preserved, since after step 2 they will be.
4. **Staleness banner.** A `version`-only query re-run on window focus and
   before submit, showing a dismissible "changed by someone else since you
   opened it" banner. This is the cheap half of what an edit lock promises -
   see [Would an edit lock be better?](#would-an-edit-lock-be-better).

**Not included** (dropped from the first draft): the three-way merge and the
per-field "keep mine / use theirs" conflict UI. Rationale in that section.

Deliberately unchanged: `expectedVersion` is still sent and still checked
server-side. The merge is a **recovery** path layered on top of the rejection,
never a replacement for it - the server must still be able to say no.

### V2.2-12: character counters on every bounded field

Fixes F24, and is what makes the UTF-16 counting decision fair to the user
rather than merely convenient for the implementation.

1. New `lib/components/CharCounter.svelte`: renders `1,847 / 2,000`, counted
   with JS `.length` so it is **the same number** the server enforces and the
   same number `maxlength` stops at.
2. States: neutral; a warning style within the last 10%; an at-limit style with
   an `aria-live="polite"` announcement ("Character limit reached") so the stop
   is not silent for screen-reader users either.
3. Applied to every bounded free-text field - comment text and author, synopsis,
   biography, title, original title, name, place of birth, character name - not
   only the two that have a `maxlength` today.
4. Where the value contains non-BMP characters, the counter is the explanation
   for why an emoji moved it by 2. No extra copy for the common case; a `title`
   tooltip on the counter covers it.

### V2.2-13: seed comments in the importer

Seed a deterministic set of comments per movie - varying length, and some with
emoji - so the comment UI, its pagination, and the emoji rules are all
demonstrable on a freshly seeded stack.

**This cannot be done safely until comments have an idempotency key** (F25).
Every other importer write is idempotent by a provenance id (`tmdbId`,
`tmdbCreditId`), comments have no such key, and they cannot be individually
deleted (v2 design decision 5) - so a naive rerun would duplicate the entire
seeded set and break the "import rerun produces the same counts" property the
verification checklist claims. Order matters here:

1. **Migration** `V7__add_comment_seed_key.sql`: `seed_key VARCHAR(100)` on
   `movie_comment`, nullable, with a partial unique index
   (`WHERE seed_key IS NOT NULL`) so ordinary user comments are unaffected.
2. **Schema/domain:** optional `seedKey` on `AddMovieCommentInput`;
   `CommentUseCases.addComment` upserts by it when present, exactly as
   `createMovie` upserts by `tmdbId`. Absent - the only case the UI ever
   produces - behavior is unchanged: always insert.
3. **Importer:** a `CommentsPort` alongside the existing ports (never a direct
   DB write - see [ports.ts](../../../demo/importer/src/ports.ts)), a
   `comments.ts` fixture module, and a deterministic per-movie selection keyed
   off `tmdbId` so a rerun produces byte-identical comments. Seed key
   `seed:<tmdbId>:<n>`. `MovieOutcome` gains `commentsImported`, and
   `ImportReport` aggregates it.
4. **Fixture content**, per movie, deliberately spanning the interesting cases:
   - one short comment (a few words)
   - one long comment near the 2 000 limit, to exercise truncation, wrapping
     and the counter
   - one emoji-only or emoji-heavy comment, including a ZWJ sequence, to prove
     the comment path really accepts what
     [V2.2-06](#v22-06-character-class-screening-on-text-fields) says it does
   - one with an emoji in the **author display name**
   - enough total comments on at least one movie to push past the 10-per-page
     size and exercise the pager
   - author names drawn from a fixed list; no real people, nothing that reads
     as a real review of a real film
5. **Tests:** the importer fake asserts a rerun yields identical comment counts
   and content; a GraphQL integration test asserts `seedKey` upserts rather than
   duplicates; and the seeded emoji comment is the natural fixture for the
   emoji round-trip test.

A note on scope: this puts fabricated user-written content into a demo
database. That is fine for a seeded demo, and the fixture list should stay
obviously synthetic - short generic reactions, not plausible-looking reviews
attributed to plausible-looking names.

### V2.2-14: test inventory gaps

See the next section; land the tests with the slice that introduces each rule
and add the rows to
[verification/v2-test-inventory.md](../../verification/v2-test-inventory.md) as
they go green.

---

## Test coverage gaps this plan closes

Answering "does the inventory already check extremities and invalid
characters?" — **partly, and not where it matters here.**

**Already covered:** pagination limit/offset clamps (1/100/0/negative), batch
ID cap, image size/empty/truncated/spoofed/unsupported, storage-key shapes and
traversal, comment blank and over-limit author/text, genre/year filter
out-of-range, name/title blank and overlong at the unit level, and `%`/`_`/
apostrophe/Unicode/mixed-case handling **in search patterns**
(`PersonSearchTest`, `PeopleSearchIntegrationTest`, `SearchUseCasesTest`).

**Not covered anywhere — the actual gap:**

| Gap | Nothing asserts… |
|---|---|
| Date extremity | any release/birth/death date bound; there is no date-validation test because there is no date validation |
| Malformed date coercion | the `code` returned for `releaseDate: "3/3/52452242"`; no test exercises the `Date` scalar's failure path end to end |
| Unicode **in stored fields** | only search *patterns* are Unicode-tested; nothing stores a name with astral characters and reads it back |
| Code-point vs UTF-16 length | the emoji-name boundary (F7) |
| Control / NUL / bidi characters | any character-class rejection, in any field |
| `synopsis` / `biography` bounds | that a 1 MB synopsis is rejected (it isn't) |
| Malformed UUID | that `movie(id: "abc")` is `NOT_FOUND` rather than `INTERNAL_ERROR` |
| `STORAGE_UNAVAILABLE` copy | that the BFF has a message for a code the backend actually emits |
| BFF status mapping | that an outage during upload isn't reported as 415 |
| Field-level error rendering | that any field is ever marked `aria-invalid` |
| Message specificity | that a validation failure shows *what* is wrong, not the generic banner |
| Emoji storage | that an emoji - let alone a ZWJ sequence - survives create → store → read; the DB encoding is inherited, never asserted |
| Emoji policy | that comments accept emoji and metadata fields reject them |
| Character counting | that the server bound, `maxlength`, and any counter agree on one unit |
| Concurrent edits | that two people editing **different** fields can both succeed - today they cannot (F21) |
| Conflict recovery | that a conflict preserves the user's input rather than discarding it |
| Credit plausibility | that a person born after a movie's release cannot be credited on it |
| Injection defenses / CSP | that the escaping holds under a script payload, and that the BFF sends a CSP at all - it currently sends none. Deferred to [v2.3](../v2.3/README.md) with the fix |
| Comment seeding | that an importer rerun does not duplicate seeded comments - there is no key to make that true yet |

### New tests to add

Backend — `RulesTest` / `PersonRulesTest` (Unit + Edge case):

- `validateReleaseDate accepts the 1888-10-14 floor and rejects the day before`
- `validateReleaseDate accepts a date five years out and rejects five years and a day`
- `validateBirthDate rejects a future date, naming the value`
- `validateBirthDate rejects a date less than two years ago and accepts exactly two`
- `validateBirthDate rejects a year before 1850`
- `validateDeathDate rejects a future date`
- `validateLifeDates names both values when death precedes birth`
- `validateLifeDates rejects an implausible lifespan over 130 years`
- `date rules read today from the injected clock, not the system clock`
- `text length is counted in code points, so a 300-emoji title is accepted`
- `text length message reports the actual entered count`
- `synopsis over 5000 characters is rejected` / same for `biography`
- `TextRules rejects NUL, C0/C1 controls, bidi overrides, and zero-width characters`
- `TextRules allows newlines and tabs in long text and rejects them in single-line fields`
- `TextRules NFC-normalizes so decomposed and precomposed forms are treated alike`
- `both services' TextRules copies accept and reject the identical vector set`
- `emoji are rejected in title, name, synopsis, biography and characterName`
- `emoji are accepted in comment text and author display name`
- `a ZWJ sequence survives the comment screen intact, joiners and all`
- `a zero-width joiner is still rejected in a metadata field`
- `a zero-width joiner between two ordinary letters is rejected even in a comment`
- `a credit is rejected when the person was not yet born at the movie's release`
- `a credit for a person who was 1 at release is allowed - only unborn is rejected`
- `length is counted in UTF-16 units, so a 1000-emoji comment is rejected at the 2000 bound`

Backend — `CatalogueGraphQlIntegrationTest` (Integration + Edge case):

- `a malformed releaseDate is BAD_USER_INPUT with the offending value in the message, not INTERNAL_ERROR`
- `a malformed expectedVersion is BAD_USER_INPUT`
- `a malformed movie/person/credit id is NOT_FOUND, not INTERNAL_ERROR`
- `a validation error carries extensions.field naming the GraphQL field`
- `an out-of-range release date is rejected at the GraphQL boundary`
- `an INTERNAL_ERROR message still leaks no cause, class name, or SQL`
- `a name with astral-plane characters round-trips through create and read`
- `an emoji comment, including a ZWJ sequence, round-trips through addMovieComment and comments`
- `the catalogue and people databases are UTF8-encoded` (a guard on the pinned `POSTGRES_INITDB_ARGS`)
- `a seeded comment upserts by seedKey instead of duplicating on a second call`
- `two updates touching different fields both succeed without a conflict`
- `addCredit rejects a person born after the movie's release, naming both dates`
- `updateMovie rejects a release date that precedes a credited person's birth, naming them`
- `updateMovie names up to three affected people and summarises the rest`
- `updateMovie refuses the save with DEPENDENCY_UNAVAILABLE when People is unreachable`
- `a conflict on the same field returns both values rather than a bare rejection`

Backend — `PeopleGrpcContractTest` / `PeopleGrpcClientTest`:

- `INVALID_ARGUMENT carries the field name in metadata and maps to a ValidationException that keeps it`
- `a person with a future birth date is INVALID_ARGUMENT`

Frontend — `errors.test.ts`:

- `every code the backend can emit has a message` — enumerated against the
  codes actually produced by `GraphQlExceptionResolver`, `ArtworkExceptionAdvice`
  and `PhotoExceptionAdvice`, so a new backend code can't silently fall through
  to the internal-error copy again (**this test would have caught F10**)
- `messageForValidation prefers the server message and falls back to the generic copy`

Frontend — new `page.server.test.ts` cases on all four form routes:

- `a malformed date is rejected before any GraphQL call, naming the value`
- `a non-integer runtime is reported rather than silently dropped`
- `a validation failure returns a fieldError keyed to the offending input`
- `an upload failure maps each code to its own status (413/415/404/503), not 415 for everything`
- `an update failure maps DEPENDENCY_UNAVAILABLE to 503, not 409`
- `the empty-file guard returns a specific message, not the generic banner`
- `the edit action returns values so the form can re-render them`
- `only changed fields are sent in the update mask`
- `a conflict returns the submitted values and a message saying they were kept`
- `the staleness check reports a moved version without blocking the form`

Frontend — component tests:

- `FieldError.test.ts`: `associates the message with its control via aria-describedby and aria-invalid`
- movie/person form tests: `marks the offending field invalid and moves focus to it after a failed submit`
- `DateField.test.ts`: `passes min and max through to the native input`
- `CreditDialog.test.ts`: `shows an inline message when the person search request fails`
- `+error.svelte` test: `renders 404 and 503 inside the app shell with the server message`
- `CharCounter.test.ts`: `counts in UTF-16 units so an emoji advances it by two`; `announces the limit politely instead of stopping input silently`
- comment form test: `the counter matches the server bound exactly at the limit`
- staleness banner test: `appears when the version has moved and is dismissible`

E2E — extend `journey.spec.ts` (coordinate with V2-17 in
[release.md](../v2/release.md) rather than duplicating it):

- one negative pass: submit a person with a future birth date, assert the field
  is highlighted and the message names the date, correct it, and continue.

Importer (`demo/importer/src/importer.test.ts`):

- `seeds the fixture comments for each movie`
- `a rerun produces identical comment counts and content`
- `a movie with enough seeded comments spans more than one comment page`

### Expected inventory movement

The current summary counts 208 tests (64 Unit / 104 Edge case / 39 Integration
/ 1 E2E). This plan adds roughly 70–85, overwhelmingly **Edge case**, in a new
"Validation & error handling" section. Counts here are an estimate to size the
work — the inventory is only updated from tests that actually exist.

---

## Decisions taken

Confirmed on review of the first draft; recorded so they are not reopened by
accident.

| Question | Decision |
|---|---|
| Movie announcement horizon | **10 years**, derived from the longest real announcement lead times |
| `synopsis` / `biography` bound | **5 000** UTF-16 units |
| Birth-date floor | **`today - 2 years`**, justified by casting-to-release lead time; infant roles are typically uncredited, shared between twins, or credited to the older actor |
| Lifespan sanity cap | **130 years** |
| Counting unit | **UTF-16 code units**, declared at every layer; a non-BMP character counts as two, and a visible counter makes that fair |
| Emoji in `comment.text` | **Allowed** |
| Emoji in `comment.authorDisplayName` | **Allowed**, subject to the same control-character, bidi, zero-width and newline screening as everything else |
| Credit age | Not born by release is an **error**, raised on submit and naming the person. Under-3 advisory **deferred** - an actor can genuinely have been 1 at release |
| People unreachable on a credit-age check | **Refuse the save** with `DEPENDENCY_UNAVAILABLE`, matching how `addCredit` already behaves - not save-without-checking |
| Concurrent editing | **Narrow mask + preserve input + staleness banner.** No three-way merge, no per-field conflict UI, no edit lock |

## Open questions

1. **Whether the deferred under-3 advisory is ever wanted.** Deferred here
   because it would fire on legitimate historical data. If it comes back, it
   would need a smarter trigger than a flat age threshold - e.g. only flagging
   when the person has no other credits within a decade of that release.
