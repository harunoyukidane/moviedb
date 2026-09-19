# Verification

```yaml
status: current
canonical_for: test-strategy
last_verified: 2026-09-19
```

Strategy (this file) changes slowly. Evidence — test counts, what's actually
been run — is kept in per-release files that go stale fast:

- [test-specs.xlsx](test-specs.xlsx) — **every automated test in the repository**, one row each: id, name, type, module under test, description, steps, expected result, actual result, status. Generated from the test sources and the runner output, so it is regenerated rather than hand-edited.
- [v1-acceptance.md](v1-acceptance.md) — v1 sign-off, evidence per acceptance item
- [v2-acceptance.md](v2-acceptance.md) — v2 sign-off, updated as tasks in [plans/v2/](../plans/v2/README.md) land
- [v2-test-inventory.md](v2-test-inventory.md) — the v2-scoped narrative inventory, grouped by feature rather than by file
- [loadtest-findings.md](loadtest-findings.md) — concurrency bugs found (and fixed) by `demo/loadtest`

The workbook holds **699 tests, all passing**: 378 backend (Kotlin/JUnit), 279
frontend (Vitest), 39 importer (Vitest) and 3 end-to-end (Playwright). No status in
it is assumed — each row's result is read from that runner's own report (Gradle's
JUnit XML, Vitest's JSON, Playwright's JSON), and a test with no report entry is
written as "Not run" rather than green. The Summary sheet names the report and
its timestamp per suite; read it before quoting a status.

**383 of the 699 are edge cases** — boundaries, error paths, missing or empty
data, degraded fallbacks — marked `(edge)` in the Test Type column. That flag is
derived from the test's name and assertions, not stored in the source; it agrees
with the 224 tests [v2-test-inventory.md](v2-test-inventory.md) classifies by
hand at 93% precision and 93% recall, so treat it as a reliable filter rather
than a guarantee for any single row.

Each row's Description is written for a reader who has not seen the codebase: what
the module is for, what that one case checks, why it matters where the test says
so, and a plain reading of any jargon. The module blurbs are hand-written in
[`scripts/test_spec_notes.py`](../../scripts/test_spec_notes.py) — edit the prose
there, not the spreadsheet.

Rebuild it with [`scripts/build-test-specs.py`](../../scripts/build-test-specs.py),
which takes the test list from the sources and the results from those reports:

```bash
cd backend && ./gradlew test
cd ../frontend && npx vitest run --reporter=json --outputFile=../.test-results/vitest.json
npx playwright test --reporter=json > ../.test-results/playwright.json
cd ../demo/importer && npx vitest run --reporter=json --outputFile=../../.test-results/importer.json
cd ../.. && python scripts/build-test-specs.py
```

Do not read historical test counts in `v1-acceptance.md` as covering v2 work —
they don't. Each acceptance file states its own scope and date.

## Test pyramid

| Level | Focus | Representative cases |
|---|---|---|
| Domain/unit | Fast business rules | cast requires character; role/category agreement; inactive/unknown codes; negative runtime/order; duplicate credit; person-in-use deletion |
| Application/unit | Use-case orchestration | batched IDs; gRPC timeout mapping; no repository write after validation failure; compensation after artwork metadata failure |
| Repository/integration | PostgreSQL + Flyway | constraints, indexes/query semantics, pagination, optimistic lock, case-insensitive search, cascade movie-credit delete |
| Contract/API integration | GraphQL and gRPC | schema shape, null/error mapping, validation, protobuf statuses, multipart artwork |
| Frontend component | User-observable UI | loading/empty/error, validation, stale search cancellation, confirmation dialog, accessible labels |
| Object-storage integration | Real MinIO via Testcontainers | store/open/exists/delete/list, missing key, content integrity, bucket isolation, service media lifecycle |
| Importer/unit | Demo seeding against a faked catalogue | idempotent rerun; unmapped genres and crew jobs dropped; TMDB 401/404/429/5xx/timeout/malformed payload; missing poster; partial credits; bounded concurrency |
| E2E | Critical journeys only | create person → create movie → add credit → upload artwork → search → remove credit → delete movie → delete person; plus long-word layout containment and the BFF's security headers |

## Edge-case checklist

Blank/whitespace/overlong names and titles · invalid dates and death before birth
· runtime zero/negative and billing order negative · duplicate TMDB IDs and
duplicate credits · same human name for different people · one person with
multiple roles/characters · unknown/inactive genre and role codes, role/category
mismatch, duplicate movie-genre assignment · movie/person/credit not found ·
maximum page size and invalid offsets · literal `%`, `_`, apostrophes, Unicode,
mixed-case search · optimistic-lock conflict from two editors · empty,
truncated, spoofed, oversized, unsupported artwork · filename traversal attempts
and duplicate artwork replacement · People response missing/out-of-order IDs ·
gRPC deadline/unavailable/invalid argument · TMDB 401/404/429/timeout/malformed
payload/missing poster/partial credits · import rerun produces the same counts ·
genre/year filter combinations, totals, offset reset, duplicate-free joins ·
cluster/list switching preserves offset and renders fallbacks/truncation ·
blank/overlong comments, reverse chronological ordering, timestamp ties,
pagination, movie cascade deletion · MinIO unavailable/missing object,
compensation failure, paginated listing, persistence across service restart ·
concurrent double-delete of the same movie/person (see
[loadtest-findings.md](loadtest-findings.md)) · artwork upload racing a
concurrent delete of its movie · dates before cinema existed, in the future, or
implying an implausible lifespan · lengths at the exact bound, one over, and
counted in code points rather than UTF-16 units · control characters, `U+0000`,
zero-width and bidi-override characters in stored text · malformed UUIDs and
malformed date/number scalars surfacing as user-input errors rather than
internal errors · a validation failure naming the offending field and value ·
emoji and ZWJ sequences surviving store-and-read where permitted and rejected
where not · two editors changing different fields of one record · conflict
recovery preserving the user's input · importer rerun not duplicating seeded
comments · a person credited on a movie released before they were born, on both the
add-credit and edit-release-date paths · a script payload stored in a comment
rendering as escaped text.

### Coverage of the checklist

The checklist above is the intended standard, not an automatic claim of coverage.
Re-checked on 2026-09-19 against [test-specs.xlsx](test-specs.xlsx): **the three
items this section previously recorded as missing have all since shipped, with
tests.** They are listed here because the gap they describe was real for a while
and the correction is easy to lose:

- **Date range, future dates, plausibility.** `MovieRules.RELEASE_DATE_FLOOR` is
  1888-10-14 (the first film); `PersonRules` rejects future birth and death dates
  and caps a lifespan at 130 years, on top of `death >= birth`. V2.8-03 added the
  cross-record rule — a person's dates must not contradict a credited movie's
  release date, enforced on all three write paths.
- **Character-class screening.** `TextRules.screen()` exists in both services
  (deliberately duplicated, not shared) and rejects `U+0000`, C0/C1 control
  characters, bidi overrides and zero-width characters. The exact message wording
  is pinned by a `TextRulesFrontendContractTest` in each service, because
  `frontend/src/lib/errors.ts` still parses it (V2.7-04).
- **Unicode as a stored value, not just a search pattern.** Emoji round-trip
  through `addMovieComment`, a ZWJ survives between two emoji but is rejected
  between letters, and catalogue metadata rejects emoji where a comment allows
  them.

Do not re-assert a gap in this file from memory. Filter the workbook by module
and read the rows — that is what caught this one.

Historical context for the gaps as they stood:
[v2-test-inventory.md](v2-test-inventory.md#known-coverage-gaps-validation--error-handling)
enumerates them, [plans/v2.2/README.md](../plans/v2.2/README.md) is the plan that
closed them, and the injection/CSP subset was sequenced separately in
[plans/v2.3/README.md](../plans/v2.3/README.md).

Coverage percentage is a diagnostic, not the goal. Prioritize decisions and
failure paths. A reasonable gate is 80% line coverage for service modules while
avoiding meaningless getter/generated-code tests.
