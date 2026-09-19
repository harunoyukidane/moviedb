# V2.8: long-word layout, save feedback, person-date/credit conflicts

```yaml
status: current
canonical_for: v2.8-backlog
last_verified: 2026-09-19
```

All items below have been implemented (see [Status](#status)).

Three items (V2.8-01..03) are user-reported defects in the person/movie edit and
detail flow. Two (V2.8-04, V2.8-05) are carried over from the third review pass
on 2026-09-19, against `main` at `bfc68fd`. V2.8-06 was added mid-implementation
(user-reported: "back" always goes to the unfiltered list instead of wherever
the user actually came from).

All five original items were grounded against the current source before being
written down — where the finding differs from how it was first described, the
item says so.

## Status

| Item | Area | Severity | Status |
|---|---|---|---|
| [V2.8-01](#v28-01-wrap-long-unbroken-text-instead-of-breaking-the-layout) | A 300-char unbroken "word" breaks the detail page | P2 | done |
| [V2.8-02](#v28-02-make-the-existing-save-confirmation-actually-land) | Save confirmation exists but is easy to miss | P3 | done |
| [V2.8-03](#v28-03-reject-birthdeath-dates-that-conflict-with-existing-credits) | Birth/death dates can contradict credited movies | P2 | done |
| [V2.8-04](#v28-04-escape-the-control-characters-in-the-contract-tests) | Contract tests are binary to git | P2 | done |
| [V2.8-05](#v28-05-fix-the-v27-plan-intro-line) | v2.7 plan intro contradicts its own status | P4 | done |
| [V2.8-06](#v28-06-back-should-return-to-where-the-user-came-from) | "Back" always goes to the unfiltered list, not the actual previous page | P3 | done |

## V2.8-01: wrap long unbroken text instead of breaking the layout

**Severity: P2.** User-reported: entering a few-hundred-character word and saving
leaves the detail page "screwed up".

**Confirmed, and it is not a validation bug — the input is legal.** The backend
length caps allow exactly this:

| Field | Cap | Owner |
|---|---|---|
| `name`, `placeOfBirth` | 300 | `PersonRules` |
| `biography` | 5000 | `PersonRules` |
| `title`, `originalTitle` | 300 | `MovieRules` |
| `synopsis` | 5000 | `MovieRules` |
| `characterName` | 300 | `CreditRules` |
| `sourceRoleName` | 150 | `CreditRules` |
| `authorDisplayName`, `text` | — | comment rules |

None of them require whitespace, so a 300-character single token is valid input
that the UI must render. **Do not "fix" this by tightening the caps** — that
would reject legitimate long values (real titles, long place names) and would
not help the many fields that legitimately hold long prose.

**The actual gap:** `grep -rn "overflow-wrap\|word-break\|word-wrap"` over
`frontend/src` returns **nothing**. The only related rule is the
`.text-truncate` utility in `app.css`. With no wrapping rule, a long token
cannot break and overflows its container, widening the page.

**Steps:**

1. Add a global wrapping rule in
   [`app.css`](../../../frontend/src/app.css) rather than per-page fixes — this
   affects at least the person detail, movie detail, both list views, credit
   rows, and comments. `overflow-wrap: anywhere` is the right primitive;
   `word-break: break-all` is not (it breaks *every* word, not just
   unbreakable ones).
2. **Add `min-width: 0` to the flex/grid children that hold this text.** This is
   the step that is usually missed and it is why a wrap rule can look like it
   "didn't work": a flex item defaults to `min-width: auto`, which refuses to
   shrink below its content's intrinsic width, so the overflow persists no
   matter what wrapping the text itself is allowed. The detail pages and
   `.entity-card` / `.poster-card` rows are the ones to check.
3. Decide per surface whether wrapping or truncation is wanted. A 300-character
   name in a grid card probably wants `.text-truncate` (which already exists)
   plus a `title` attribute; the same name on the detail page wants to wrap and
   be fully readable. Don't apply one blanket answer to both.
4. Check the headings specifically — `<h1>Edit "{person.name}"</h1>` on the edit
   page and the detail `<h1>` interpolate unbounded user text directly.
5. Add a regression test with a 300-character token asserting the container does
   not exceed the viewport width, so this cannot silently return.

## V2.8-02: make the existing save confirmation actually land

**Severity: P3.** Reported as "not obvious that it has been saved".

**Correction to the framing: the confirmation already exists.**
[`people/[id]/edit/+page.svelte:97`](../../../frontend/src/routes/people/[id]/edit/+page.svelte)
renders `<StateBanner variant="info">Changes saved.</StateBanner>` when
`form?.updated` is set, and the action does return `{ updated: true }` rather
than redirecting. [`StateBanner`](../../../frontend/src/lib/components/StateBanner.svelte)
already carries `role="status"`, so assistive tech is announced correctly.

So this is a **prominence and position** problem, not a missing feature:

- The banner renders immediately after the `<h1>`, at the top of a long form
  (details + photo + danger sections). Saving from a field near the bottom
  leaves the confirmation off-screen entirely — the user sees nothing change.
- Nothing moves focus or scroll to it, so there is no cue that anything
  happened.
- It persists indefinitely rather than reading as a fresh event, so on a second
  save it is ambiguous whether the banner is new or left over.

**Steps:**

1. Move focus to the banner on save (a `tabindex="-1"` container plus `.focus()`
   once `form.updated` flips). This fixes the visual cue and the keyboard
   experience in one move, and scrolls it into view for free.
2. Consider also placing a confirmation near the submit control, so the feedback
   appears where the user's attention already is. If both exist, only one should
   be announced — two live regions saying the same thing is worse than one.
3. Make repeat saves legible: either auto-dismiss after a few seconds, or key
   the banner so Svelte re-creates the node each save and `role="status"`
   re-announces it. Prefer re-announcing over a timed dismissal, which can
   vanish before a slower reader reaches it.
4. Apply the same treatment to the movie edit page, which has the same shape.
5. While here, confirm the photo-upload and photo-delete confirmations
   (`photoUploaded` / `photoDeleted`) behave the same way — they share this
   page and the same problem.

## V2.8-03: reject birth/death dates that conflict with existing credits

**Severity: P2.** User-reported: a person can be given a birth date of 2024 or a
death date of 1953 while credited on a 2003 movie, and nothing objects.

### Where this check belongs — and where it must not go

People Service owns `birthDate`/`deathDate`, but **People must never know about
movies**: "People never depends on Catalogue" (overview.md §6, ADR-1). Credits
and movie release dates are Catalogue-owned. So the check cannot live in People.

It belongs in Catalogue's
[`PersonUseCases.updatePerson`](../../../backend/catalogue-service/src/main/kotlin/com/moviecatalogue/catalogue/application/PersonUseCases.kt),
which today is a bare passthrough to `peopleClient.updatePerson`. The precedent
is directly above it in the same file: `deletePerson` checks
`credits.existsByPersonId` and throws `PersonInUseException` before calling
People (ADR-6). This item is the same shape — Catalogue validates against its
own data, then delegates.

`creditsForPerson` in that file already does exactly the needed fetch
(`credits.findAllByPersonId` → `movies.findAllById`), so the data access pattern
is established; it just doesn't currently read `releaseDate`.

**Known and accepted limitation:** like the safe-delete, this check is
bypassable by calling People's gRPC directly, since People cannot enforce it.
That is consistent with ADR-6 and should be stated in the ADR/doc, not silently
assumed.

### The rule is not symmetric — decide this before implementing

- **Birth after release: a hard error.** Someone cannot appear in a film
  released before they were born. No legitimate case.
- **Death before release: NOT automatically invalid.** Posthumous releases are
  routine — an actor dies during production or post, and the film releases
  months or years later. A naive "died before release date" block would reject
  real, correct data.

The reported case (died 1953, credited on a 2003 movie) is clearly wrong, but it
is wrong because of the *magnitude* of the gap, not its direction. Options:

- **A grace window** (recommended): reject only when release is more than N
  years after death. Configurable; N of about 5 covers delayed productions and
  archive-footage credits while still catching the 50-year case.
- **Warn and confirm:** surface the conflict but let the user proceed. Most
  faithful to reality, but there is no existing confirm-to-proceed pattern for
  mutations in this codebase, so it is the larger change.
- **Hard block both directions:** simplest, and wrong. Do not pick this without
  checking the data first — see below.

**Before implementing, run the proposed rule against the seeded catalogue.** The
TMDB-seeded demo data is real-world data and very likely contains at least one
legitimate posthumous credit. If the rule would reject records the importer
itself creates, the rule is wrong — and it would also break re-seeding.

### Steps

1. Add a read in Catalogue that returns each credited movie's `releaseDate` for
   a person (extend the existing credits→movies fetch).
2. In `PersonUseCases.updatePerson`, when `birthDate` or `deathDate` is present
   in the command, load the person's credited movie release dates and apply the
   rule above. Skip the lookup entirely when neither date changed, so the common
   edit path costs nothing extra.
3. Throw a domain exception mapped to a new stable `extensions.code` —
   `PERSON_DATE_CONFLICTS_CREDIT` or similar, following `PERSON_IN_USE`. Add it
   to the error table in
   [interfaces.md](../../architecture/interfaces.md).
4. Make the message actionable: name the conflicting movie(s) and their release
   dates, not just "conflict". The user needs to know *which* credit to fix.
   Cap the list — a person with 40 conflicting credits should not produce a
   40-line error.
5. Surface it as a field error on `birthDate`/`deathDate` in the BFF
   (`+page.server.ts` already maps codes to `fieldErrors`), so it appears
   against the offending input rather than as a generic banner.
6. Tests: birth after a credited release (rejected), death long before a
   credited release (rejected), death shortly before release (allowed —
   posthumous), person with no credits (unaffected), and an edit that changes
   neither date (no extra query).

### Scope note — the other two doors

The same contradiction can be created from two directions this item does **not**
close:

- adding a credit linking a person to a movie whose release date already
  conflicts, and
- editing a movie's `releaseDate` so it conflicts with an existing credit's
  person.

Closing all three is the only way to make the invariant actually hold. This item
covers the reported path (person edit) deliberately; the other two should be
their own item once the rule and its grace window are settled, so the rule is
agreed in one place before being enforced in three.

## V2.8-04: escape the control characters in the contract tests

**Severity: P2.** From the third review pass.

Both `TextRulesFrontendContractTest.kt` files (catalogue and people) embed **raw
control bytes** in their string literals instead of Kotlin escapes — a NUL
(`0x00`) around line 20 and a BEL (`0x07`) around line 41, plus raw U+200B and
U+202E. Git classifies both files as binary:

```
backend/.../TextRulesFrontendContractTest.kt | Bin 0 -> 2510 bytes
backend/.../TextRulesFrontendContractTest.kt | Bin 0 -> 2497 bytes
```

Consequences:

- **The files cannot be diffed or reviewed.** Any future change shows only a
  byte count, and a merge conflict cannot be resolved textually.
- **U+202E is the "Trojan Source" character** — a right-to-left override in
  source is exactly what security scanners and GitHub flag, because it can make
  code render differently from how it compiles. Benign and intentional here, but
  it will trip tooling.
- A test asserting *"we reject dangerous invisible characters"* is itself
  written using raw dangerous invisible characters.

**Steps:** replace the raw literals with Kotlin unicode escapes — `\u0000`
for NUL, `\u200B` for the zero-width space, `\u202E` for the bidi override and
`\u0007` for BEL, so `"a<NUL>b"` is written `"a\u0000b"`. (This paragraph must
name the escapes rather than paste the characters: written the other way it
made *this file* binary to git too.) Semantics are identical, the files
become reviewable text, and `git diff` works again. Worth doing before these
files accumulate history. Confirm with `git diff --stat` that neither reports
`Bin` afterwards.

**Scope was wider than the two files first named here.** A sweep of every
tracked text file afterwards found the same raw bytes in three more:
`catalogue-service`'s `RulesTest.kt` (NUL, SOH), `people-service`'s
`PersonRulesTest.kt` (two NULs), and **this plan document itself**, whose
"replace the literals" sentence was written with the literals. All five are
now escaped and `git diff` reports text for every one. Worth re-running the
sweep rather than fixing named files:

```bash
git ls-files "*.kt" "*.ts" "*.svelte" "*.md" | python -c "
import sys
for f in sys.stdin.read().split():
    b = open(f,'rb').read()
    if any(c < 9 or c in (11,12) or 13 < c < 32 or c == 127 for c in b):
        print(f)
"
```

(Deliberately free of backslash escapes: a `tr`/`grep` version of this sweep
has to spell the control characters out, and writing it into a document is how
this file acquired a NUL byte twice.)

## V2.8-05: fix the v2.7 plan intro line

**Severity: P4.** [plans/v2.7/README.md](../v2.7/README.md) carries
`status: done` and a status table where all six rows read `done`, but its intro
still opens "Nothing here is started." One sentence, and it is the same
doc-drift pattern the v2.5 correction was about.

**Steps:** rewrite that line to describe the delivered state, the way the v2.6
plan was updated when its work landed.

## V2.8-06: "back" should return to where the user came from

**Severity: P3.** User-reported, added mid-implementation: the back link on the
movie/person detail pages (and the "new movie"/"new person" forms) always goes
to the unfiltered `/movies` or `/people` list, even when the user arrived from
a filtered list, from alphabet-jump pagination, or by clicking through a credit
from a different record (movie → person, person → movie). Since both list
pages already have their own top-level nav links, a fixed "all movies"/"all
people" destination on the back link is redundant with those and loses the
user's place.

**Implemented as a stack that pops.** `afterNavigate` in the root layout
([`+layout.svelte`](../../../frontend/src/routes/+layout.svelte)) feeds an
in-app history stack
([`$lib/stores/navigation.ts`](../../../frontend/src/lib/stores/navigation.ts)),
and [`BackLink`](../../../frontend/src/lib/components/BackLink.svelte) points
at the top of it, falling back to a fixed destination only when there is no
in-app page to return to (direct link, bookmark, hard refresh, and SSR).

**A single "previous page" slot does not work, and the first implementation of
this item shipped one.** It recorded the page navigated away from on every
navigation — including the navigation the back link itself caused — so going
back from A to B immediately recorded A as B's previous page:

```text
/movies → /movies/M          back → /movies         correct
/movies/M → /movies/M/edit
edit's back → /movies/M      back → /movies/M/edit   into the edit form
```

From there the two pages pointed at each other forever and the list was
unreachable. Same shape via a credit: movie → person → back → movie → back →
person. A one-slot pointer is a worse browser back button — it pushes where a
back should pop.

The stack fixes it with one rule: navigating to the entry on top of the stack
is a *return*, so it pops rather than pushing. That covers the back link and
the browser's own back button identically, and needs no click handler —
`BackLink` stays a plain `<a href>`, so middle-click, open-in-new-tab and the
no-JS case all keep working.

Two details that make it behave the way a back button should:

- **Entries are `pathname + search`.** Filters, cluster/list view, offset and
  the alphabet jump all live in the query string, so back lands on the state
  the user left rather than a reset list.
- **Consecutive navigations within one page collapse.** Re-filtering, paging or
  typing in search is one destination to the user; without collapsing, back
  would step through every intermediate filter.
- **Form pages are never back destinations.** A `/new` or `/edit` page the user
  has left is dropped rather than pushed, so creating a person and then pressing
  back goes to the list they started from instead of the blank form they just
  submitted (the double-submit trap), and an abandoned edit form does not
  resurface later. Arriving at a form still records the page it was opened
  from, so the form's own back link is unaffected.

The edit pages' "← Back to movie"/"← Back to person" links now use `BackLink`
too, with the record as their fallback. Leaving them as plain links is what
poisoned the stack in the first version: they pushed a fresh entry instead of
popping, so returning from an edit form left the detail page pointing back into
it. The label stays specific because the only way to reach an edit form is from
that record's own detail page, so the destination really is always that record.

Covered by [`navigation.test.ts`](../../../frontend/src/lib/stores/navigation.test.ts),
which walks whole browsing sessions rather than setting the store directly —
the previous component-level tests passed against the ping-ponging version.

## Verification state

Updated after the pass; supersedes the "not run here" notes this section
carried while the work was in progress.

- **Frontend: green** - `npm test` -> 51 files, **279 passed**, 0 failed.
  Coverage added for `StateBanner` autofocus, the `app.css` long-word rules,
  `DateField`'s error association, the `PERSON_DATE_CONFLICTS_CREDIT` rewrites
  in `errors.ts`, the back stack (whole browsing sessions, not just store
  writes), and a second consecutive save re-announcing.
- **`npm run check`: 0 errors**, the same pre-existing unused-CSS warning as
  before (`MovieFilters.svelte`) - unrelated to this pass.
- **Playwright: green** - 3 passed against the composed stack (~10s). Getting
  there turned up a defect in the test rather than the product, worth recording
  because the failure mode is easy to repeat:
  - `journey.spec.ts` had been red since `0b27e95` ("lighthouse fix"), which
    replaced the credit dialog's suggestion `<button>` with a non-focusable
    `<li role="option">` - the ARIA combobox pattern the V2.6 a11y work
    adopted. The test still asked for
    `getByRole('button', { name: personName })`, which cannot match an option,
    so it hung to the 30s timeout at step 3.
  - **An a11y refactor that changes an element's role silently invalidates
    every role-based selector aimed at it.** `npm run check` and the component
    tests cannot see this; only the e2e run can.
  - It stayed hidden because on a cold stack the run failed earlier, at step
    1's 5s visibility assertion, so the real failure never got a turn. A first
    red step is not necessarily the only red step.
  - The e2e step entered CI in `bfc68fd` ("2.7 fixes"), *after* `0b27e95`
    landed, and CI only triggers on `main` - so nothing had run this spec
    end-to-end since the break.
  - The journey also deleted its movie but never its person, so every green
    run left an `E2E Person <stamp>` row in the catalogue permanently. It now
    deletes the person as step 8; the four accumulated rows were removed.
  - The layout assertion for V2.8-01 - a 300-character unbroken name must not
    widen the person page at 1280px or 375px - lives in the same file and is
    the only test that measures rendered layout; `app.css.test.ts` only pins
    the rules and cannot prove the page is correct.
- **Backend: green**, confirmed by the maintainer running
  `./gradlew :catalogue-service:test '-PdockerApiVersion=1.44'` outside the
  sandbox. Gradle cannot run inside it (`java.io.IOException: Unable to
  establish loopback connection`, from bash, PowerShell and `gradlew.bat`
  alike), so the `PersonUseCasesTest` cases, the new
  `CreditRulesFrontendContractTest` and the two added credit/release path
  cases are always verified outside.
- **V2.8-03's grace window (5 years) was checked against the live seeded
  catalogue**, not just reasoned about: querying the running local stack
  (1477 people / 123 movies) found the worst legitimate posthumous gap is
  Leigh Brackett / *The Empire Strikes Back* at 2.17 years, comfortably inside
  5 years. The handful of birth-after-release rows carrying a "2024" birth date
  are the maintainer's own manual test records, not seed data, and the new
  check does not retroactively touch them either way - it only runs when
  `birthDate`/`deathDate` is part of an actual edit.

## Sequencing

V2.8-01 and V2.8-02 are small, independent, and both fix things a user can see
today — do them first. V2.8-04 is mechanical and should land before those two
test files gain history. V2.8-03 is the substantial one: settle the posthumous
rule and check it against the seeded data **before** writing code, because
picking the rule wrong means either rejecting valid records or shipping an
invariant that does not hold. V2.8-05 is a one-line cleanup that can ride with
anything. V2.8-06 is independent of the rest and was implemented last, after
being reported mid-pass.

## Follow-ups not covered here

V2.8-03's [scope note](#scope-note--the-other-two-doors) has since been
**closed**. It correctly predicted the hole: the first implementation guarded
only the person-edit path, so the reported conflict (died 1953, credited on a
2003 film) stayed reachable simply by setting the death date first and adding
the credit second - the rule applied or not depending on the order of two
edits. All three write paths now enforce both halves:

| Path | Check |
|---|---|
| Person edit | `CreditRules.validatePersonDatesAgainstCredits` |
| Add credit | `CreditRules.validateCreditDates` (was birth-only) |
| Movie release date | `validateCreditedPeopleAgainstReleaseDate` (was birth-only) |

The credit paths keep raising `ValidationException`/`BAD_USER_INPUT` against
the field the user actually touched (`personId`, `releaseDate`), rather than
`PERSON_DATE_CONFLICTS_CREDIT`, whose copy is written for the person-edit
direction.
