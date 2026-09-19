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

**Steps:** replace the literals with Kotlin unicode escapes — `"a b"`,
`"a​b"`, `"a‮b"`, `"abcd"`. Semantics are identical, the files
become reviewable text, and `git diff` works again. Worth doing before these
files accumulate history. Confirm with `git diff --stat` that neither reports
`Bin` afterwards.

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

**Implemented:** `afterNavigate` in the root layout
([`+layout.svelte`](../../../frontend/src/routes/+layout.svelte)) records the
pathname+search of the page navigated away from into a small store
([`$lib/stores/navigation.ts`](../../../frontend/src/lib/stores/navigation.ts)).
A new [`BackLink`](../../../frontend/src/lib/components/BackLink.svelte)
component reads that store and falls back to a fixed destination only when
there is no in-app previous page (a direct link, bookmark, or hard refresh).
Wired into the movie/person detail pages and the "new movie"/"new person"
pages, replacing the hardcoded `← All movies` / `← All people` links with a
plain `← Back` that goes wherever the user actually came from.

The edit pages' "← Back to movie"/"← Back to person" links were left as-is —
they always point at the record being edited regardless of history, which is
already correct (that's the only page you can reach an edit form from).

## Verification state at the time of writing

- **Frontend: green** — `npm test` → 50 files, **~270 passed** (added coverage
  for `StateBanner` autofocus, the `app.css` long-word rules, `BackLink`, and
  the new `PERSON_DATE_CONFLICTS_CREDIT` rewrites in `errors.ts`), 0 failed.
- **`npm run check`: 0 errors**, the same pre-existing unused-CSS warning as
  before (`MovieFilters.svelte`) — unrelated to this pass.
- **Backend: not run** (sandbox blocks Gradle — `java.io.IOException: Unable to
  establish loopback connection` from both bash and PowerShell, including via
  `gradlew.bat` directly; consistent with prior passes). The new
  `PersonUseCasesTest` cases (V2.8-03) and the two rewritten contract test
  files (V2.8-04) are reviewed carefully against existing working examples in
  the same files but **not compiled**. Run
  `./gradlew :catalogue-service:test '-PdockerApiVersion=1.44'` outside the
  sandbox to confirm before merging.
- **V2.8-03's grace window (5 years) was checked against the live seeded
  catalogue**, not just reasoned about: querying the running local stack
  (1477 people / 123 movies) found the worst legitimate posthumous gap is
  Leigh Brackett / *The Empire Strikes Back* at 2.17 years, comfortably inside
  5 years, and 2 pre-existing birth-after-release rows in the seed data
  (`Alan Keyes`, `Ken Davitian` — both born "2024", clearly bad TMDB import
  data) that the new check does not retroactively touch, since it only runs
  when `birthDate`/`deathDate` is part of an actual edit.

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

V2.8-03's [scope note](#scope-note--the-other-two-doors) still stands after
implementation: `CreditUseCases.addCredit` and
`MovieUseCases.validateCreditedPeopleAgainstReleaseDate` already reject a
birth date after a release date (V2.2-03b, predates this plan), but neither
checks the new death-date-vs-grace-window rule this item added. A person's
dates and their credits can still disagree if the contradiction is created by
adding a new credit or moving a movie's release date, rather than by editing
the person - closing all three surfaces for both rules is its own item.
