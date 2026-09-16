# Verification

```yaml
status: current
canonical_for: test-strategy
last_verified: 2026-09-15
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
concurrent delete of its movie.

Coverage percentage is a diagnostic, not the goal. Prioritize decisions and
failure paths. A reasonable gate is 80% line coverage for service modules while
avoiding meaningless getter/generated-code tests.
