# V2.7: search offset bounds + V2.6 follow-ups

```yaml
status: done
canonical_for: v2.7-backlog
last_verified: 2026-09-19
```

Nothing here is started. This plan is the output of a **second** code review run
on 2026-09-19, against `main` at `7e7ee50` — after the whole of
[v2.6](../v2.6/README.md) had landed (PRs #3, #4, #5).

Two items are follow-ups on V2.6 code that shipped (V2.7-02, V2.7-03). The rest
come from ground the first review never covered: unified search, the error-message
layer, and CI.

**Read [What is already right](#what-is-already-right) before changing the media
or combobox paths.** The second review re-verified those and they are correct;
the remaining issues are narrower than they might look from the item titles.

## Status

| Item | Area | Severity | Status |
|---|---|---|---|
| [V2.7-01](#v27-01-bound-the-unified-search-offset) | Unbounded `offset` → 500 + heap amplification | **P1** | done |
| [V2.7-02](#v27-02-make-the-eager-image-count-work-at-ssr-time) | V2.6-07's measurement runs too late to matter | P2 | done |
| [V2.7-03](#v27-03-let-the-webp-semaphore-degrade-instead-of-queueing) | `Semaphore.acquire()` has no timeout | P3 | done |
| [V2.7-04](#v27-04-stop-parsing-backend-prose-in-the-frontend) | Validation copy duplicated in 3 places, unguarded | P3 | done (contract test, not the structural fix — see item) |
| [V2.7-05](#v27-05-close-the-two-ci-gaps) | No E2E job; smoke test never hits the proxy | P4 | done |
| [V2.7-06](#v27-06-let-modifier-key-combinations-through-the-number-guard) | Number guard swallows Ctrl/Cmd shortcuts | P4 | done |

## Verification state at the time of review

Everything below was confirmed by running it, not by reading:

- **Frontend: green.** `npm test` → 48 files, 241 tests, 0 failures (was 14 red
  before v2.6).
- **`npm run check`: 0 errors**, 1 pre-existing unused-CSS warning in
  `MovieFilters.svelte`. The a11y gate is genuinely restored — the V2.6-02 guard
  in `source-guard.test.ts` enforces single-rule, justified suppressions rather
  than banning them outright, which correctly still permits the backdrop-dismiss
  cases in `ConfirmDialog`/`CreditDialog`.
- **Dependencies:** `npm audit --omit=dev` → **0 vulnerabilities**. All 11
  advisories are dev-only; the critical `vitest` RCE requires its API/UI server to
  be listening. Routine bump, not a fix-now.
- Backend was not re-run in this pass; it was green on 2026-09-19 via
  `./gradlew test '-PdockerApiVersion=1.44'` (quote the flag in PowerShell).

## V2.7-01: bound the unified search offset

**Severity: P1.** A live 500 and a heap-exhaustion path from one unauthenticated
GET, on a published port.

**Reproduced against the running stack:**

```bash
curl -s -X POST http://localhost:8080/graphql -H "Content-Type: application/json" \
  -d '{"query":"query{ search(query:\"a\", page:{limit:10, offset:2147483647}){ movies{id} } }"}'
# → {"errors":[{"message":"internal error", ... "code":"INTERNAL_ERROR"}]}
```

Service log:
`java.lang.IllegalArgumentException: Page size must not be less than one`
at [`SearchUseCases.kt:70`](../../../backend/catalogue-service/src/main/kotlin/com/moviecatalogue/catalogue/application/SearchUseCases.kt).

Root cause is [`MovieRules.clampOffset`](../../../backend/catalogue-service/src/main/kotlin/com/moviecatalogue/catalogue/domain/Rules.kt),
which is `if (requested < 0) 0 else requested` — no upper bound. That yields two
distinct defects in `SearchUseCases.search`:

1. **Integer overflow → `INTERNAL_ERROR`.** `fetchWindow = clampedOffset +
   clampedLimit` wraps negative and `PageRequest.of(0, negative)` throws. A
   malformed page request belongs in `BAD_USER_INPUT`, not an internal error.
2. **Heap amplification, no overflow needed.** `offset=100000` gives
   `PageRequest.of(0, 100100)`, so `searchByTitlePattern` hydrates up to 100k
   `Movie` JPA entities in order to `.drop(100000).take(10)`. At the
   requirements.md ceiling that is the whole movie table pulled into heap per
   request.

**Why this is unified search specifically.** The endpoints that received the
V2.5-02 offset pushdown degrade cleanly — verified:

```
movies(offset: 2147483647) → {"total":122,"offset":2147483647,"items":[]}   200
people(offset: 2147483647) → {"total":1476,"offset":2147483647,"items":[]}  200
search(offset: 2147483647) → INTERNAL_ERROR
```

Unified search is the last path that still materialises `offset + limit` rows in
application memory. [V2.5-02](../v2.5/README.md) deliberately left it that way
under Option B and priced the work as "a moderate optimization rather than a
correctness-adjacent fix like V2.5-01". That pricing was about *latency* and
looks incomplete in hindsight: the in-memory window is also the resource-
exhaustion surface. **This item does not require Option A** — it only requires a
bound.

**Steps:**

1. Short-circuit before any I/O: if `clampedOffset` is at or beyond a maximum
   search window, return an empty `SearchResultView` without touching the DB or
   gRPC. That matches what `movies`/`people` already do at deep offsets and
   removes the overflow path entirely.
2. Otherwise compute the window in `Long` (or via `Math.addExact`) so an
   overflow can never reach `PageRequest.of` again.
3. Pick the bound deliberately and write down why. Note the trade-off before
   reaching for the obvious "just clamp it in `clampOffset`":
   - Clamping inside `clampOffset` is one line but **silently changes
     `movies`/`people` semantics** — a deep offset would start returning the page
     at the clamped offset instead of an empty page. That is a behaviour change
     to two currently-correct endpoints.
   - Bounding inside `SearchUseCases` only is narrower and leaves the other two
     endpoints exactly as they are. **Prefer this** unless you want the error
     contract unified, in which case do it at the GraphQL boundary and return
     `BAD_USER_INPUT` consistently for all three.
4. Add a test at the boundary asserting `offset = Int.MAX_VALUE` returns an empty
   result (or `BAD_USER_INPUT`) rather than `INTERNAL_ERROR`, and one asserting a
   large-but-valid offset issues no oversized fetch.

**While you are in this file** (non-blocking, do not let it expand the change):
`CompletableFuture.supplyAsync` runs the JDBC call on the common `ForkJoinPool`,
which is visible in the stack trace above. That both escapes the method's
`@Transactional(readOnly = true)` context and puts blocking I/O on a pool shared
process-wide. Worth its own item later; it is not what makes V2.7-01 a P1.

## V2.7-02: make the eager image count work at SSR time

**Severity: P2.** A regression introduced by V2.6-07's fix.

[`MovieClusterView.svelte`](../../../frontend/src/lib/features/movies/MovieClusterView.svelte)
initialises `eagerCount = 2` and only measures the real column count in
`onMount`. But `loading` is a **server-rendered attribute**, and the browser's
preload scanner acts on it at parse time — long before hydration. The
measurement therefore cannot influence the load it exists to optimise.

**Verified in the HTML the stack actually serves** (`curl http://localhost:4173/movies`):

```
1  fetchpriority="high"   ← eager
2  (eager)
3  loading="lazy"         ← first row on a 6-column desktop grid
4  loading="lazy"
5  loading="lazy"
6  loading="lazy"
```

Four of the six first-row posters ship as `lazy` — exactly the deprioritisation
the component's own comment says must be avoided. The pre-V2.6 hardcoded
`EAGER_COUNT = 6` was *correct on desktop* at SSR time and merely wasteful on
mobile; the current version is wrong everywhere at the only moment that counts.

**Steps:**

1. Decide where the column count can be known **before** the HTML is sent.
   Options, roughly in order of cost: pick a fixed SSR default matching the
   widest common grid and drop the runtime measurement (restores the old
   desktop-correct behaviour, accepts mobile waste); or derive it from the
   `view` query parameter / a stored preference the loader already sees; or
   express the first row in CSS so the distinction stops depending on an
   attribute at all.
2. Whatever is chosen, the resize listener should go or become inert — it
   currently rewrites `loading` on images that have already loaded.
3. Guard the measurement if it survives: `getComputedStyle(...).gridTemplateColumns`
   returns `"none"` when the element is not laid out as a grid, and
   `"none".split(' ').length` is `1`, which would set `eagerCount = 1` — worse
   than the default.
4. Measure with Lighthouse mobile **and** desktop before and after. This is an
   optimization; it should be justified by a number. The same `index < 6`
   hardcode is still in
   [`PersonListRow.svelte`](../../../frontend/src/lib/features/people/PersonListRow.svelte)
   and should move in step with whatever is decided here.

## V2.7-03: let the WebP semaphore degrade instead of queueing

**Severity: P3.** V2.6-04 did the important half correctly — concurrent `cwebp`
processes are capped at 4 and the permit handling is right (`acquired` flag,
release in `finally`, no leak on the exception path).

What remains: [`permits.acquire()`](../../../backend/media/src/main/kotlin/com/moviecatalogue/media/WebpEncoder.kt)
blocks **indefinitely**. Processes are bounded; waiting request threads are not.
Under an upload burst, callers queue at roughly `depth / 4 × timeoutSeconds`,
holding Tomcat threads the whole time.

That contradicts the principle stated in the same file's own docblock — "a
missing binary, a timeout, or a failed conversion all degrade to *no WebP
variant* rather than failing the upload". Waiting for a permit is the one case
that blocks instead of degrading.

**Steps:**

1. Replace `acquire()` with `tryAcquire(n, TimeUnit.SECONDS)`; on failure log and
   return `null`, which the caller already handles as "no variant this time".
   Choose the wait budget relative to `timeoutSeconds` and state it in the
   docblock next to the existing degradation list.
2. Destroy the process if `waitFor` is interrupted. Right now the
   `InterruptedException` propagates without `destroyForcibly()`, orphaning a
   running `cwebp`.
3. Add a test that a caller which cannot get a permit within the budget returns
   `null` promptly rather than blocking.

## V2.7-04: stop parsing backend prose in the frontend

**Severity: P3.** Working correctly today — this is about how it fails later.

[`errors.ts`](../../../frontend/src/lib/errors.ts) rewrites backend validation
messages with regexes such as `UNSTORABLE_CHARACTER_RE`. The patterns were
checked against the backend and **match exactly as of 2026-09-19**. The problem
is that the same prose now exists in three places with nothing binding them:

- [catalogue `TextRules.kt`](../../../backend/catalogue-service/src/main/kotlin/com/moviecatalogue/catalogue/domain/TextRules.kt) (lines 39-55)
- [people `TextRules.kt`](../../../backend/people-service/src/main/kotlin/com/moviecatalogue/people/domain/TextRules.kt) (lines 39-55) — duplicated per ADR-1
- the frontend regex

Reword a message in either service and the regex silently stops matching; the
user sees raw backend text and **no test fails**. Backend exception prose has
become an undocumented API, and it is one the two services can drift apart on
independently.

**Steps:**

1. Prefer the structural fix: carry `field` and a stable rule code in GraphQL
   `extensions` alongside the existing `extensions.code`, and have the frontend
   render copy from that data instead of parsing English. `FIELD_LABELS` in
   `errors.ts` already does exactly this for field names — the rule half is the
   missing piece.
2. If the structural fix is deferred, add a contract test that at minimum pins
   the backend message shapes, so a reword breaks a test in the service that
   changed rather than surfacing silently in the UI.
3. Either way, note in [interfaces.md](../../architecture/interfaces.md) whether
   validation message *text* is part of the contract or not. Today it
   accidentally is.

## V2.7-05: close the two CI gaps

**Severity: P4.** The workflow added in v2.6 is otherwise solid — backend,
schema generation, frontend check/test/build, importer, and a compose smoke test.

Two holes:

1. **No Playwright job.** `npm run test:e2e` and the full create person → create
   movie → add credit → upload artwork → search → remove credit → delete journey
   exist but never run automatically. It is the only test covering the whole
   path. `compose-smoke` already stands the stack up, which is where it belongs.
2. **`compose-smoke` never touches the browser entry point.** It curls
   `http://localhost:8080/actuator/health/readiness` only. It never requests
   `http://localhost:4173`, so the **proxy** — the newest component, the one that
   had no healthcheck until V2.6-08, and now the only published port — is not
   smoke-tested at all.

**Steps:**

1. Add `curl -fsS http://localhost:4173/ > /dev/null` to `compose-smoke`. One
   line, and it covers the entire browser-facing chain.
2. Add an E2E job (or extend `compose-smoke`) that seeds enough data to run the
   journey and executes `BASE_URL=http://localhost:4173 npm run test:e2e`. Note
   the seeding constraint: CI has no `TMDB_READ_TOKEN`, so either the journey
   must create everything it needs, or a small fixture path is needed.

## V2.7-06: let modifier key combinations through the number guard

**Severity: P4.**
[`blockNonWholeNumberKeys`](../../../frontend/src/lib/numberInput.ts) calls
`preventDefault()` on `-`, `+`, `e`, `E`, `.` unconditionally, so `Ctrl+E` /
`Cmd+E` browser shortcuts are swallowed while focus is in a number field.

**Steps:** return early when `event.ctrlKey || event.metaKey || event.altKey` is
set. Note also that paste bypasses the guard entirely, so it is a convenience,
not a validation boundary — the server check remains the real defense, which the
existing comment already says.

## What is already right

Re-verified during the second review; do not undo these.

- **The combobox keyboard fix (V2.6-01) is a correct APG pattern.**
  `aria-activedescendant` on the input, stable per-option ids, wrap-around
  arrows, `Enter` to pick, `Escape` to close, options deliberately
  non-focusable. It fixed the lockout without reintroducing the nested-interactive
  ARIA violation.
- **The a11y guard (V2.6-02) is better than what the v2.6 plan proposed.** It
  requires each `svelte-ignore` covering an `a11y-*` rule to name exactly one
  rule and carry a `--` justification, rather than banning them — so legitimate
  backdrop-dismiss suppressions still pass while a blanket one cannot land.
- **The sweeper age guard (V2.6-03) uses an injected `Clock`** and a configurable
  `PT15M` default in both services, so it is testable and tunable.
- **`acceptsWebp` (V2.6-06) is correct**, including the `q=0` refusal and the
  fallback to the primary asset for absent, wildcard, or unparseable headers.
- **End-to-end WebP negotiation survives the full chain.** Through
  browser → Caddy → BFF → catalogue, verified:
  `Accept: image/webp` → `Content-Type: image/webp`; `Accept: image/png` →
  `image/jpeg`; distinct ETags per variant; and `Vary: Accept` **preserved
  through Caddy**, which does not clobber it when adding its own compression
  headers.
- **Docs were kept in step with the code this time** — the v2.6 status table and
  the routing doc were both updated as the work landed, which is the drift that
  produced half of the first review.

## Sequencing

V2.7-01 first and on its own: it is the only item with a live failure and a
resource-exhaustion path, and the narrow fix does not touch the merge/rank
design. V2.7-02 next — it is a regression on shipped code and currently makes
desktop LCP worse than it was before v2.6. V2.7-05 is two lines of real
protection for the least-covered component and can ride along with anything.
V2.7-03, V2.7-04 and V2.7-06 are opportunistic; V2.7-04 is the only one that may
turn into a larger contract change, so scope it before starting it.
