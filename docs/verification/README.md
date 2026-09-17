# Verification

```yaml
status: current
canonical_for: test-strategy
last_verified: 2026-09-17
```

Strategy (this file) changes slowly. Evidence — test counts, what's actually
been run — is kept in per-release files that go stale fast:

- [v1-acceptance.md](v1-acceptance.md) — v1 sign-off, evidence per acceptance item
- [v2-acceptance.md](v2-acceptance.md) — v2 sign-off, updated as tasks in [plans/v2/](../plans/v2/README.md) land
- [loadtest-findings.md](loadtest-findings.md) — concurrency bugs found (and fixed) by `demo/loadtest`

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
| E2E | Critical journeys only | create person → create movie → add credit → upload art → filter/view → comment → search → remove/delete |

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

### Not yet implemented

The checklist above is the intended standard, not a claim of coverage. As of
2026-09-17 these items in it have **no** implementing test, and one is not even
implemented in the product: "invalid dates and death before birth" is only half
true — `death >= birth` is enforced, but no date range, future, or
plausibility rule exists; and "Unicode" is exercised only in search patterns,
never as a stored field value. Character-class screening (control characters,
`U+0000`, bidi overrides) is absent from both the product and the tests.

See [v2-test-inventory.md](v2-test-inventory.md#known-coverage-gaps-validation--error-handling)
for the enumerated gaps and [plans/v2.2/README.md](../plans/v2.2/README.md) for
the plan that closes them; the injection/CSP subset is sequenced separately in
[plans/v2.3/README.md](../plans/v2.3/README.md), after v2.2 is verified.

Coverage percentage is a diagnostic, not the goal. Prioritize decisions and
failure paths. A reasonable gate is 80% line coverage for service modules while
avoiding meaningless getter/generated-code tests.
